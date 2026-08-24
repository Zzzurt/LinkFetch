"""抖音作品解析器。

流程：展开短链 -> 从重定向链提取 video/note/slides ID -> 依次尝试移动/PC 分享页
-> 解析 _ROUTER_DATA -> 构造无水印播放直链。

2026-08 抖音改版后分享页不再内嵌作品数据，web 接口对图文笔记存在 images_base
过滤。因此新增两条免签名回退：
1. 视频：PC 详情接口 aweme/v1/web/aweme/detail/ + 爬虫 UA（Googlebot）直接返回
   完整 aweme 数据（含无水印播放直链）；
2. 图文笔记：SEO 页面 application/ld+json（article）拿到标题/作者/图片列表，
   可绕过 images_base 过滤。
"""
from __future__ import annotations

import json
import re
from typing import List, Optional

from ..models import (
    PARSE_FAILED,
    RATE_LIMITED,
    MediaItem,
    ParseError,
    ParseResponse,
    Platform,
)
from .base import BaseParser

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "https://www.douyin.com/",
}

_ITEM_RE = re.compile(r"/(?:video|note|slides)/(\d+)")
_ROUTER_MARKER = "window._ROUTER_DATA"
_SHARE_BASES = ["https://m.douyin.com", "https://www.iesdouyin.com"]
_PLAY_URL = "https://aweme.snssdk.com/aweme/v1/play/?video_id={video_id}&ratio=1080p&line=0"
_SPIDER_UA = (
    "Mozilla/5.0 (compatible; Googlebot/2.1; "
    "+http://www.google.com/bot.html)"
)
_SPIDER_HEADERS = {
    **HEADERS,
    "User-Agent": _SPIDER_UA,
    "Accept": "application/json, text/plain, */*",
}
_PC_DETAIL_API = "https://www.douyin.com/aweme/v1/web/aweme/detail/"
_NOTE_PAGE_URL = "https://www.douyin.com/note/{content_id}"
_NOTE_SHARE_URL = "https://m.douyin.com/share/note/{content_id}/"
_LD_JSON_RE = re.compile(
    r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',
    re.S | re.I,
)



class DouyinParser(BaseParser):
    platform = Platform.DOUYIN

    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        headers = dict(HEADERS)
        if cookie:
            headers["Cookie"] = cookie

        resp = await self.client.get(url, headers=headers, follow_redirects=True)
        # 重定向链中任意 URL 都可能带作品 ID（移动 UA 可能落在 iesdouyin.com/share/slides/...）
        content_id = None
        kind = "note"
        for candidate in [str(resp.url)] + [str(h.url) for h in resp.history]:
            match = _ITEM_RE.search(candidate)
            if match:
                content_id = match.group(1)
                if "/video/" in candidate:
                    kind = "video"
                break
        if not content_id:
            raise ParseError(PARSE_FAILED, "无法从抖音链接中提取视频 / 笔记 ID", http_status=400)

        # 依次尝试多个分享页路径（移动站优先，图文笔记另试 slides 路径）
        attempts: List[str] = []
        for base in _SHARE_BASES:
            attempts.append(f"{base}/share/{kind}/{content_id}/")
            if kind == "note":
                attempts.append(f"{base}/share/slides/{content_id}/")

        last_error: Optional[ParseError] = None
        for share_url in attempts:
            try:
                page = await self.client.get(share_url, headers=headers, follow_redirects=True)
                if page.status_code in (403, 429):
                    raise ParseError(RATE_LIMITED, "抖音触发了风控，请稍后重试")
                state = self._extract_router_data(page.text)
                item = self._find_item(state)
                if item is not None:
                    return self._build_response(item)
            except ParseError as exc:
                last_error = exc

        # 分享页全部失败后的新回退：
        # 1) PC 详情接口（爬虫 UA 免签名）——视频作品可拿到无水印播放直链；
        # 2) SEO 页 JSON-LD——图文笔记可绕过 images_base 过滤；
        # 3) 旧的 iteminfo 接口兜底。
        spider_headers = dict(_SPIDER_HEADERS)
        if cookie:
            spider_headers["Cookie"] = cookie

        try:
            api = f"{_PC_DETAIL_API}?aweme_id={content_id}"
            page = await self.client.get(api, headers=spider_headers)
            if page.status_code in (403, 429):
                raise ParseError(RATE_LIMITED, "抖音触发了风控，请稍后重试")
            detail = (page.json().get("aweme_detail")) or None
            if detail:
                return self._build_response(detail)
        except ParseError:
            raise
        except (ValueError, json.JSONDecodeError) as exc:
            raise ParseError(PARSE_FAILED, "抖音接口返回异常") from exc

        if kind == "note":
            try:
                return await self._parse_note_via_seo(content_id, spider_headers)
            except ParseError as exc:
                last_error = exc

        # 分享页全部失败时回退到 iteminfo 接口
        try:
            api = f"{_SHARE_BASES[0]}/web/api/v2/aweme/iteminfo/?item_ids={content_id}"
            page = await self.client.get(api, headers=headers)
            if page.status_code in (403, 429):
                raise ParseError(RATE_LIMITED, "抖音触发了风控，请稍后重试")
            items = (page.json().get("item_list")) or []
            if items:
                return self._build_response(items[0])
        except ParseError:
            raise
        except (ValueError, json.JSONDecodeError) as exc:
            raise ParseError(PARSE_FAILED, "抖音接口返回异常") from exc

        if last_error is not None:
            raise last_error
        raise ParseError(PARSE_FAILED, "抖音解析失败，作品可能已删除")

    @staticmethod
    def _extract_router_data(html: str) -> dict:
        start = html.find(_ROUTER_MARKER)
        if start < 0:
            raise ParseError(PARSE_FAILED, "抖音分享页无数据（可能被风控拦截）")
        brace = html.find("{", start)
        if brace < 0:
            raise ParseError(PARSE_FAILED, "抖音分享页无数据（可能被风控拦截）")
        depth = 0
        in_string = False
        escaped = False
        end = -1
        for i in range(brace, len(html)):
            ch = html[i]
            if in_string:
                if escaped:
                    escaped = False
                elif ch == "\\":
                    escaped = True
                elif ch == '"':
                    in_string = False
                continue
            if ch == '"':
                in_string = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i
                    break
        if end < 0:
            raise ParseError(PARSE_FAILED, "抖音分享页无数据（可能被风控拦截）")
        blob = html[brace : end + 1]
        blob = re.sub(r"\bundefined\b", "null", blob)
        blob = re.sub(r"\bNaN\b", "null", blob)
        try:
            return json.loads(blob)
        except json.JSONDecodeError as exc:
            raise ParseError(PARSE_FAILED, "抖音分享页数据解析失败") from exc

    @staticmethod
    def _find_item(state: dict) -> Optional[dict]:
        loader = state.get("loaderData") or {}
        for value in loader.values():
            if not isinstance(value, dict):
                continue
            video_res = value.get("videoInfoRes") or {}
            item_list = video_res.get("item_list") or []
            if item_list:
                return item_list[0]
            note_res = value.get("noteInfoRes") or {}
            note = note_res.get("note") or {}
            detail = note.get("note_detail")
            if detail:
                return detail
        return None

    async def _parse_note_via_seo(
        self, content_id: str, headers: dict
    ) -> ParseResponse:
        """图文笔记：从 SEO 页面的 JSON-LD(article) 提取标题/作者/图片。"""
        last_error: Optional[ParseError] = None
        for url in (
            _NOTE_SHARE_URL.format(content_id=content_id),
            _NOTE_PAGE_URL.format(content_id=content_id),
        ):
            try:
                page = await self.client.get(
                    url, headers=headers, follow_redirects=True
                )
                if page.status_code in (403, 429):
                    raise ParseError(RATE_LIMITED, "抖音触发了风控，请稍后重试")
                item = self._ld_json_to_item(self._extract_ld_json(page.text))
                if item is not None:
                    return self._build_response(item)
            except ParseError as exc:
                last_error = exc
        raise last_error or ParseError(
            PARSE_FAILED, "抖音笔记页无数据（可能被风控拦截）"
        )

    @staticmethod
    def _extract_ld_json(html: str) -> Optional[dict]:
        """从 SEO 页面提取 application/ld+json 中的 article 结构化数据。"""
        for match in _LD_JSON_RE.finditer(html):
            try:
                data = json.loads(match.group(1))
            except json.JSONDecodeError:
                continue
            if not isinstance(data, dict):
                continue
            if "article" in str(data.get("@type", "")).lower() and data.get("image"):
                return data
        return None

    @staticmethod
    def _ld_json_to_item(ld: Optional[dict]) -> Optional[dict]:
        """把 JSON-LD article 转成与 item_list 兼容的 dict，便于复用 _build_response。"""
        if not ld:
            return None
        image_urls = ld.get("image") or []
        images = [
            {"url_list": [url]}
            for url in image_urls
            if isinstance(url, str) and url
        ]
        if not images:
            return None
        author_obj = ld.get("author") or ld.get("creator") or {}
        author_name = author_obj.get("name") if isinstance(author_obj, dict) else None
        desc = (
            ld.get("articleBody")
            or ld.get("headline")
            or ld.get("name")
            or "抖音作品"
        )
        item: dict = {"desc": desc, "images": images}
        if author_name:
            item["author"] = {"nickname": author_name}
        return item

    def _build_response(self, item: dict) -> ParseResponse:
        desc = item.get("desc") or item.get("video_text") or "抖音作品"
        author = (item.get("author") or {}).get("nickname") or None

        medias: List[MediaItem] = []
        for image in item.get("images") or []:
            url_list = image.get("url_list") or []
            if url_list:
                medias.append(MediaItem(kind="image", url=url_list[0], quality="original"))

        video = item.get("video") or {}
        play_addr = video.get("play_addr") or {}
        uri = play_addr.get("uri") or ""
        if uri.startswith("http"):
            video_url = uri
        elif uri:
            video_url = _PLAY_URL.format(video_id=uri)
        else:
            video_url = ""
        cover = ((video.get("cover") or {}).get("url_list") or [None])[0]
        if video_url:
            medias.insert(0, MediaItem(kind="video", url=video_url, cover=cover, quality="1080p"))

        if not medias:
            raise ParseError(PARSE_FAILED, "该作品不包含图片或视频")
        media_type = self._media_type(medias)
        return ParseResponse(
            platform=Platform.DOUYIN,
            title=desc,
            author=author,
            type=media_type,
            medias=medias,
        )

    @staticmethod
    def _media_type(medias: List[MediaItem]) -> str:
        has_video = any(m.kind.value == "video" for m in medias)
        has_image = any(m.kind.value == "image" for m in medias)
        if has_video and has_image:
            return "mixed"
        if has_video:
            return "video"
        return "image"

