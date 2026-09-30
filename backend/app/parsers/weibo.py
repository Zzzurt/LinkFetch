"""微博解析器。

流程：展开短链（t.cn）-> 提取微博 ID -> 调 m.weibo.cn statuses/show 接口
-> 提取视频直链与多图原图（优先 mw2000 大图）。

混合发布（图文 + 视频混排）的帖子，接口的 pics / mix_media_ids 只覆盖前 9 个媒体，
完整图片清单在 pic_ids 里，按 mix_media_ids 的轮次还原顺序并用 pic_ids 补全。
"""
from __future__ import annotations

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
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "https://m.weibo.cn/",
    # m.weibo.cn 接口要求该头，否则返回访客系统页面
    "X-Requested-With": "XMLHttpRequest",
}

# 移动端 /status/<id> 与桌面端 /weibo.com/<uid>/<id> 两种形态
_STATUS_RE = re.compile(r"/(?:status|detail)/([A-Za-z0-9_]+)")
_UID_STATUS_RE = re.compile(r"/(\d+)/([A-Za-z0-9_]+)")
_FID_RE = re.compile(r"[?&]fid=1034:(\d+)")
_HTML_TAG_RE = re.compile(r"<[^>]+>")
# 任意 wx 域名均可取原图，pic_id 可拼出接口未返回明细的图片直链
_LARGE_PIC_URL = "https://wx1.sinaimg.cn/large/{pid}.jpg"


class WeiboParser(BaseParser):
    platform = Platform.WEIBO

    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        headers = dict(HEADERS)
        if cookie:
            headers["Cookie"] = cookie

        # 优先从原始链接直接提取（桌面端 weibo.com/<uid>/<id>）
        status_id = self._extract_status_id(url)
        if status_id is None:
            # 短链需要展开：检查整条重定向链中的 URL
            resp = await self.client.get(url, headers=headers, follow_redirects=True)
            for candidate in [str(resp.url)] + [str(h.url) for h in resp.history]:
                status_id = self._extract_status_id(candidate)
                if status_id:
                    break
        if not status_id:
            raise ParseError(PARSE_FAILED, "无法从微博链接中提取微博 ID", http_status=400)

        api = f"https://m.weibo.cn/statuses/show?id={status_id}"
        resp = await self.client.get(api, headers=headers)
        if resp.status_code in (403, 429):
            raise ParseError(RATE_LIMITED, "微博触发了风控，请稍后重试（可尝试在设置中配置 Cookie）")
        try:
            data = (resp.json().get("data")) or {}
        except ValueError as exc:
            raise ParseError(PARSE_FAILED, "微博接口返回异常") from exc
        if not data:
            raise ParseError(PARSE_FAILED, "微博不存在或已删除")
        return self._build_response(data)

    @staticmethod
    def _extract_status_id(url: str) -> Optional[str]:
        match = _STATUS_RE.search(url)
        if match:
            return match.group(1)
        match = _UID_STATUS_RE.search(url)
        if match:
            return match.group(2)
        match = _FID_RE.search(url)
        if match:
            return match.group(1)
        return None

    @staticmethod
    def _pic_url(pic: dict) -> str:
        """图片字段可能是字符串，也可能是 {url: ...} 对象（如 large -> mw2000）。"""
        for key in ("original", "large", "url"):
            value = pic.get(key)
            if isinstance(value, dict):
                value = value.get("url")
            if isinstance(value, str) and value.strip():
                return value.strip()
        return ""

    @classmethod
    def _image_from_pic(cls, pic: dict) -> Optional[MediaItem]:
        url = cls._pic_url(pic)
        if not url:
            return None
        return MediaItem(kind="image", url=url, quality="original")

    @staticmethod
    def _image_from_pid(pid: str) -> MediaItem:
        """接口未返回明细的图片（混合发布第 10 个媒体起）按 pic_id 拼原图直链。"""
        return MediaItem(kind="image", url=_LARGE_PIC_URL.format(pid=pid), quality="original")

    @classmethod
    def _video_from_pic(cls, pic: dict) -> Optional[MediaItem]:
        url = (pic.get("videoSrc") or "").strip()
        if not url:
            return None
        return MediaItem(kind="video", url=url, cover=cls._pic_url(pic) or None, quality="hd")

    def _build_response(self, data: dict) -> ParseResponse:
        text = _HTML_TAG_RE.sub("", data.get("text") or "").strip()[:100]
        title = text or "微博正文"
        author = (data.get("user") or {}).get("screen_name") or None

        pics = [p for p in (data.get("pics") or []) if isinstance(p, dict)]
        pic_ids = [p for p in (data.get("pic_ids") or []) if isinstance(p, str) and p]
        mix_ids = [m for m in (data.get("mix_media_ids") or []) if isinstance(m, str) and m]

        medias = (
            self._build_mixed_medias(mix_ids, pics, pic_ids)
            if mix_ids
            else self._build_plain_medias(data, pics, pic_ids)
        )
        if not medias:
            raise ParseError(PARSE_FAILED, "该微博不包含图片或视频")
        media_type = self._media_type(medias)
        return ParseResponse(
            platform=Platform.WEIBO,
            title=title,
            author=author,
            type=media_type,
            medias=medias,
        )

    @classmethod
    def _build_plain_medias(cls, data: dict, pics: List[dict], pic_ids: List[str]) -> List[MediaItem]:
        """普通微博：pics 就是完整图集，page_info 的视频（如有）排在最前。"""
        medias: List[MediaItem] = []
        page_info = data.get("page_info") or {}
        media_info = page_info.get("media_info") or {}
        video_url = (
            media_info.get("mp4_hd_url")
            or media_info.get("mp4_sd_url")
            or media_info.get("stream_url_hd")
            or media_info.get("stream_url")
            or ""
        ).strip()
        if video_url:
            cover = page_info.get("page_pic") or None
            medias.append(MediaItem(kind="video", url=video_url, cover=cover, quality="hd"))

        seen = set()
        for pic in pics:
            item = cls._image_from_pic(pic)
            if item is not None:
                medias.append(item)
                seen.add(pic.get("pid"))
        # 仅当 pics 确实不完整时才补全，避免个别响应缺 pid 时把图片重复列一遍
        if len(pics) < len(pic_ids):
            for pid in pic_ids:
                if pid not in seen:
                    medias.append(cls._image_from_pid(pid))
        return medias

    @classmethod
    def _build_mixed_medias(
        cls, mix_ids: List[str], pics: List[dict], pic_ids: List[str]
    ) -> List[MediaItem]:
        """混合发布：mix_media_ids 给出前 9 个媒体（图与视频混排）的种类与顺序。

        接口对混合发布只返回前 9 个媒体（pics / mix_media_ids 均被截断），
        图片完整清单在 pic_ids 里——不补全的话 18 图微博只能解析出 9 个。
        mix 中的 t.cn 短链即一个视频，与 pics 里 type=video 的条目按出现顺序一一对应。
        """
        by_pid = {p.get("pid"): p for p in pics if p.get("pid")}
        video_pics = [p for p in pics if p.get("type") == "video"]

        medias: List[MediaItem] = []
        seen = set()
        video_index = 0
        for entry in mix_ids:
            if entry.startswith("http"):
                if video_index < len(video_pics):
                    item = cls._video_from_pic(video_pics[video_index])
                    if item is not None:
                        medias.append(item)
                video_index += 1
                continue
            pic = by_pid.get(entry)
            item = cls._image_from_pic(pic) if pic else None
            medias.append(item if item is not None else cls._image_from_pid(entry))
            seen.add(entry)
        for pid in pic_ids:
            if pid not in seen:
                medias.append(cls._image_from_pid(pid))
        return medias

    @staticmethod
    def _media_type(medias: List[MediaItem]) -> str:
        has_video = any(m.kind.value == "video" for m in medias)
        has_image = any(m.kind.value == "image" for m in medias)
        if has_video and has_image:
            return "mixed"
        if has_video:
            return "video"
        return "image"

