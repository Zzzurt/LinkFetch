"""X (Twitter) 解析器。
流程：提取推文 ID（t.co 短链先跟随重定向）-> 请求 syndication 半公开接口
-> 返回原图与最高画质 mp4 直链。
长视频（amplify / unified_card 卡片）的媒体不在 mediaDetails，而在 card.binding_values
（HLS / VMAP）或顶层 photos / video 字段，这里一并处理。
若 syndication 返回 TweetTombstone（受限内容，长视频常见）或两次都拿不到媒体，
自动回退 vxtwitter / fxtwitter 第三方接口。
"""
from __future__ import annotations

import json
import re
from typing import List, Optional
from urllib.parse import urlparse

import httpx

from ..models import (
    PARSE_FAILED,
    RATE_LIMITED,
    MediaItem,
    MediaKind,
    ParseError,
    ParseResponse,
    Platform,
)
from .base import BaseParser

_SYNDICATION_URL = "https://cdn.syndication.twimg.com/tweet-result"
_STATUS_RE = re.compile(r"/(?:i/)?status(?:es)?/(\d+)")
_SIZE_SUFFIX_RE = re.compile(r"_(large|thumb|small|medium|orig)(?=\.(?:jpg|jpeg|png|webp|gif))")
_MEDIA_FILE_RE = re.compile(r"<MediaFile[^>]*>\s*(.*?)\s*</MediaFile>", re.I | re.S)
_CDATA_RE = re.compile(r"<!\[CDATA\[\s*(.*?)\s*\]\]>", re.S)
_X_HOSTS = ("x.com", "twitter.com")
_GOOGLEBOT_UA = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"


class _NoMedia(Exception):
    """内部信号：响应中没有可识别的媒体。"""


class XParser(BaseParser):
    platform = Platform.X

    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        tweet_id = _extract_id(url)
        if tweet_id is None:
            host = _host(url)
            if host in _X_HOSTS or host.endswith(".x.com") or host.endswith(".twitter.com"):
                raise ParseError(PARSE_FAILED, "无法从 X 链接中提取推文 ID")
            # 短链（t.co 等）：沿重定向链查找推文 ID
            try:
                resp = await self.client.get(url)
            except httpx.HTTPError as exc:
                raise ParseError(PARSE_FAILED, f"X 短链展开失败：{exc}") from exc
            for candidate in [resp, *resp.history]:
                tweet_id = _extract_id(str(candidate.url))
                if tweet_id:
                    break
        if tweet_id is None:
            raise ParseError(PARSE_FAILED, "无法从 X 链接中提取推文 ID")

        last_body: Optional[str] = None
        # 第一遍普通 UA；若媒体为空（长视频常见），第二遍用 Googlebot UA 重试
        for attempt in range(2):
            headers = {"User-Agent": _GOOGLEBOT_UA} if attempt == 1 else None
            try:
                resp = await self.client.get(
                    _SYNDICATION_URL,
                    params={"id": tweet_id, "lang": "zh", "token": "7"},
                    headers=headers,
                )
            except httpx.HTTPError as exc:
                raise ParseError(PARSE_FAILED, f"X 接口请求失败：{exc}") from exc
            if resp.status_code == 404:
                raise ParseError(PARSE_FAILED, "推文不存在或已删除")
            if resp.status_code in (403, 429):
                raise ParseError(RATE_LIMITED, "X 触发了风控，请稍后重试")
            last_body = resp.text
            try:
                root = resp.json()
            except ValueError as exc:
                raise ParseError(PARSE_FAILED, "X 接口返回异常") from exc
            error = root.get("error")
            if error and not _is_tombstone(root):
                message = str(error).lower()
                if "no status" in message or "invalid" in message:
                    raise ParseError(PARSE_FAILED, "推文不存在或已删除")
                raise ParseError(PARSE_FAILED, f"X 解析失败：{error}")
            if _is_tombstone(root):
                break
            try:
                return await _build_response(root, self.client)
            except _NoMedia:
                # 继续下一次尝试（Googlebot UA）
                continue

        # 第三方回退：vxtwitter / fxtwitter 能拿到 syndication 拿不到的受限内容（如长视频）
        fallback = await _try_fallback(self.client, tweet_id)
        if fallback is not None:
            return fallback

        raise ParseError(PARSE_FAILED, "该推文不包含图片或视频")


async def _build_response(root: dict, client: httpx.AsyncClient) -> ParseResponse:
    raw_text = root.get("full_text") or root.get("text") or ""
    title = " ".join(str(raw_text).split())[:100] or "推文"
    user = root.get("user") or {}
    author = user.get("name") or user.get("screen_name")

    medias: List[MediaItem] = []
    candidates = [root, root.get("quoted_tweet") or {}, root.get("retweeted_tweet") or {}]
    for candidate in candidates:
        details = candidate.get("mediaDetails") or []
        if details:
            medias = _collect_media(details)
            break
        if await _collect_card_media(candidate, client, medias):
            break
    if not medias:
        _collect_top_level_media(root, medias)
    if not medias:
        raise _NoMedia()

    has_video = any(m.kind == MediaKind.VIDEO for m in medias)
    has_image = any(m.kind == MediaKind.IMAGE for m in medias)
    return ParseResponse(
        platform=Platform.X,
        title=title,
        author=author,
        type="mixed" if has_video and has_image else ("video" if has_video else "image"),
        medias=medias,
    )


async def _collect_card_media(candidate: dict, client: httpx.AsyncClient, medias: List[MediaItem]) -> bool:
    card = candidate.get("card") or {}
    card_name = str(card.get("name") or "").split(":")[-1]
    binding = card.get("binding_values") or {}
    if not card_name or not binding:
        return False

    if "unified_card" in card_name:
        unified_json = _binding_string(binding, "unified_card")
        if not unified_json:
            return False
        try:
            unified = json.loads(unified_json)
        except ValueError:
            return False
        entities = unified.get("media_entities") or {}
        items = [v for v in entities.values() if isinstance(v, dict)]
        if items:
            medias.extend(_collect_media(items))
            return bool(medias)
        return False

    hls = _binding_string(binding, "player_hls_url")
    stream = _binding_string(binding, "player_stream_url")
    vmap = _binding_string(binding, "amplify_url_vmap")
    cover = _binding_image(binding, "player_image")
    url = hls or (stream if stream and ".m3u8" in stream.lower() else None)
    if url is None:
        if vmap:
            url = await _resolve_vmap(client, vmap)
        elif stream:
            url = await _resolve_vmap(client, stream)
    if url:
        medias.append(MediaItem(kind=MediaKind.VIDEO, url=url, cover=cover, quality="hd"))
        return True
    return False


async def _resolve_vmap(client: httpx.AsyncClient, url: str) -> Optional[str]:
    try:
        resp = await client.get(url)
        if resp.status_code != 200:
            return None
    except httpx.HTTPError:
        return None
    candidates: List[str] = []
    for match in _MEDIA_FILE_RE.finditer(resp.text):
        value = match.group(1).strip()
        cdata = _CDATA_RE.search(value)
        if cdata:
            value = cdata.group(1).strip()
        if value:
            candidates.append(value)
    return (
        next((u for u in candidates if ".m3u8" in u.lower()), None)
        or next((u for u in candidates if ".mp4" in u.lower()), None)
        or (candidates[0] if candidates else None)
    )


def _collect_top_level_media(root: dict, medias: List[MediaItem]) -> None:
    for photo in root.get("photos") or []:
        if not isinstance(photo, dict):
            continue
        url = photo.get("url") or photo.get("media_url_https")
        if url:
            medias.append(MediaItem(kind=MediaKind.IMAGE, url=_original_image(url), quality="original"))
    video = root.get("video")
    if isinstance(video, dict):
        variants = video.get("variants") or []
        url = _pick_video(variants) if variants else video.get("url")
        if url:
            cover = video.get("poster") or video.get("media_url_https") or video.get("thumbnail")
            medias.append(MediaItem(kind=MediaKind.VIDEO, url=url, cover=cover, quality="hd"))


def _binding_string(binding: dict, key: str) -> Optional[str]:
    value = binding.get(key)
    if isinstance(value, dict):
        return value.get("string_value")
    return None


def _binding_image(binding: dict, key: str) -> Optional[str]:
    value = binding.get(key)
    if not isinstance(value, dict):
        return None
    image = value.get("image_value")
    if isinstance(image, dict) and image.get("url"):
        return image["url"]
    return value.get("string_value")


def _collect_media(details: list) -> List[MediaItem]:
    medias: List[MediaItem] = []
    for media in details:
        base_url = media.get("media_url_https")
        if not base_url:
            continue
        media_type = media.get("type")
        if media_type == "photo":
            medias.append(MediaItem(kind=MediaKind.IMAGE, url=_original_image(base_url), quality="original"))
        elif media_type in ("video", "animated_gif"):
            video_url = _pick_video((media.get("video_info") or {}).get("variants") or [])
            if video_url:
                medias.append(
                    MediaItem(
                        kind=MediaKind.VIDEO,
                        url=video_url,
                        cover=base_url,
                        quality="gif" if media_type == "animated_gif" else "hd",
                    )
                )
    return medias


def _pick_video(variants: list) -> Optional[str]:
    best: Optional[str] = None
    best_bitrate = -1
    hls: Optional[str] = None
    for variant in variants:
        content_type = variant.get("content_type")
        url = variant.get("url")
        if not url:
            continue
        if content_type == "video/mp4":
            bitrate = variant.get("bitrate") or 0
            if bitrate > best_bitrate:
                best_bitrate = bitrate
                best = url
        elif content_type == "application/x-mpegURL" and hls is None:
            hls = url
    return best or hls


def _original_image(base: str) -> str:
    cleaned = _SIZE_SUFFIX_RE.sub("", base)
    return f"{cleaned}&name=orig" if "?" in cleaned else f"{cleaned}?name=orig"


def _extract_id(url: str) -> Optional[str]:
    match = _STATUS_RE.search(url)
    return match.group(1) if match else None


def _host(url: str) -> str:
    try:
        return (urlparse(url).hostname or "").lower()
    except ValueError:
        return ""
def _is_tombstone(root: dict) -> bool:
    """TweetTombstone：syndication 接口拒绝展示该推文（长视频常见）。"""
    return root.get("__typename") == "TweetTombstone" or "tombstone" in root


async def _try_fallback(client: httpx.AsyncClient, tweet_id: str) -> Optional[ParseResponse]:
    """依次尝试 vxtwitter、fxtwitter；成功返回解析结果，全部失败返回 None。"""
    for builder, url in (
        (_build_vx_response, f"https://api.vxtwitter.com/i/status/{tweet_id}"),
        (_build_fx_response, f"https://api.fxtwitter.com/status/{tweet_id}"),
    ):
        try:
            resp = await client.get(url)
            if resp.status_code != 200:
                continue
            root = resp.json()
        except (httpx.HTTPError, ValueError):
            continue
        result = builder(root)
        if result is not None:
            return result
    return None


def _build_vx_response(root: dict) -> Optional[ParseResponse]:
    medias: List[MediaItem] = []
    extended = root.get("media_extended") or []
    if extended:
        for item in extended:
            if not isinstance(item, dict):
                continue
            mtype = item.get("type")
            url = item.get("url")
            if not url:
                continue
            if mtype == "image":
                medias.append(MediaItem(kind=MediaKind.IMAGE, url=url, quality="original"))
            elif mtype in ("video", "gif"):
                medias.append(
                    MediaItem(
                        kind=MediaKind.VIDEO,
                        url=url,
                        cover=item.get("thumbnail_url"),
                        quality="gif" if mtype == "gif" else "hd",
                    )
                )
    else:
        for url in root.get("mediaURLs") or []:
            if not isinstance(url, str):
                continue
            if _is_likely_video(url):
                medias.append(MediaItem(kind=MediaKind.VIDEO, url=url, quality="hd"))
            else:
                medias.append(MediaItem(kind=MediaKind.IMAGE, url=url, quality="original"))
    if not medias:
        return None
    return _fallback_response(
        root.get("text"),
        root.get("user_name") or root.get("user_screen_name"),
        medias,
    )


def _build_fx_response(root: dict) -> Optional[ParseResponse]:
    code = root.get("code")
    if code not in (None, 200):
        return None
    tweet = root.get("tweet")
    if not isinstance(tweet, dict):
        return None
    media = tweet.get("media")
    medias: List[MediaItem] = []
    if isinstance(media, dict):
        for photo in media.get("photos") or []:
            if isinstance(photo, dict) and photo.get("url"):
                medias.append(MediaItem(kind=MediaKind.IMAGE, url=photo["url"], quality="original"))
        for video in media.get("videos") or []:
            if isinstance(video, dict) and video.get("url"):
                medias.append(
                    MediaItem(
                        kind=MediaKind.VIDEO,
                        url=video["url"],
                        cover=video.get("thumbnailUrl"),
                        quality="hd",
                    )
                )
    if not medias:
        return None
    author_obj = tweet.get("author") or {}
    author = author_obj.get("name") or author_obj.get("screen_name")
    return _fallback_response(tweet.get("text"), author, medias)


def _fallback_response(raw_text, author, medias: List[MediaItem]) -> ParseResponse:
    title = " ".join(str(raw_text or "").split())[:100] or "推文"
    has_video = any(m.kind == MediaKind.VIDEO for m in medias)
    has_image = any(m.kind == MediaKind.IMAGE for m in medias)
    return ParseResponse(
        platform=Platform.X,
        title=title,
        author=author,
        type="mixed" if has_video and has_image else ("video" if has_video else "image"),
        medias=medias,
    )


def _is_likely_video(url: str) -> bool:
    lower = url.lower()
    return ".mp4" in lower or ".m3u8" in lower or "/video/" in lower
