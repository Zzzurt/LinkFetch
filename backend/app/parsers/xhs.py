"""小红书笔记解析器。

流程：展开短链 -> 提取笔记 ID 与 xsec_token -> 抓取 explore 详情页
-> 解析 __INITIAL_STATE__ -> 提取原图（fileId 转 JPEG）与无水印视频直链。
"""
from __future__ import annotations

import json
import re
from typing import List, Optional
from urllib.parse import urlsplit

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
    "Referer": "https://www.xiaohongshu.com/",
}

_NOTE_ID_RE = re.compile(r"/(?:explore|discovery/item|item)/([0-9a-fA-F]{8,})")
_XSEC_RE = re.compile(r"[?&]xsec_token=([^&]+)")
_INITIAL_STATE_MARKER = "window.__INITIAL_STATE__"


def clean_image_url(url: str) -> str:
    """旧版小红书图片 URL 清洗：带 watermark 的后缀才需要去掉，并切回原图域名。"""
    if not url:
        return url
    if "watermark" not in url.lower():
        return url
    url = url.split("!")[0]
    url = re.sub(r"\?.*$", "", url)
    return url.replace("sns-webpic", "sns-img")


class XiaohongshuParser(BaseParser):
    platform = Platform.XHS

    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        headers = dict(HEADERS)
        if cookie:
            headers["Cookie"] = cookie

        resp = await self.client.get(url, headers=headers, follow_redirects=True)
        final_url = str(resp.url)
        match = _NOTE_ID_RE.search(final_url)
        if not match:
            raise ParseError(PARSE_FAILED, "无法从小红书链接中提取笔记 ID", http_status=400)
        note_id = match.group(1)
        xsec = _XSEC_RE.search(final_url)
        xsec_token = xsec.group(1) if xsec else ""

        explore_url = f"https://www.xiaohongshu.com/explore/{note_id}"
        params = {"xsec_token": xsec_token, "xsec_source": "pc_feed"} if xsec_token else None
        page = await self.client.get(explore_url, params=params, headers=headers, follow_redirects=True)
        if page.status_code in (403, 429, 461):
            raise ParseError(RATE_LIMITED, "小红书触发了风控，请稍后重试（可尝试在设置中配置 Cookie）")

        state = self._extract_state(page.text)
        note = self._find_note(state, note_id)
        if not note:
            raise ParseError(PARSE_FAILED, "未能在页面中找到笔记数据（笔记可能已删除或需要登录）")
        return self._build_response(note)

    @staticmethod
    def _extract_state(html: str) -> dict:
        start = html.find(_INITIAL_STATE_MARKER)
        if start < 0:
            raise ParseError(PARSE_FAILED, "小红书页面结构变化，无法解析（请升级 app 或联系维护者）")
        brace = html.find("{", start)
        if brace < 0:
            raise ParseError(PARSE_FAILED, "小红书页面结构变化，无法解析（请升级 app 或联系维护者）")
        # 逐字符括号配平，精确截取 JSON 对象（字符串内可能出现 </script> 等干扰内容）
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
            raise ParseError(PARSE_FAILED, "小红书页面结构变化，无法解析（请升级 app 或联系维护者）")
        # 页面内嵌数据是 JS 字面量，可能出现 undefined / NaN 等非 JSON 值
        blob = html[brace : end + 1]
        blob = re.sub(r"\bundefined\b", "null", blob)
        blob = re.sub(r"\bNaN\b", "null", blob)
        try:
            return json.loads(blob)
        except json.JSONDecodeError as exc:
            raise ParseError(PARSE_FAILED, "小红书页面数据解析失败") from exc

    @staticmethod
    def _find_note(state: dict, note_id: str) -> Optional[dict]:
        # 新版结构：noteData.data.noteData
        note_data = (state.get("noteData") or {}).get("data") or {}
        note = note_data.get("noteData")
        if isinstance(note, dict):
            return note
        # 旧版结构：note.noteDetailMap[<id>].note
        note_map = (state.get("note") or {}).get("noteDetailMap") or {}
        entry = note_map.get(note_id) or {}
        return entry.get("note")

    def _build_response(self, note: dict) -> ParseResponse:
        title = note.get("title") or note.get("desc") or "小红书笔记"
        user = note.get("user") or {}
        author = user.get("nickName") or user.get("nickname") or None

        medias: List[MediaItem] = []
        for image in note.get("imageList") or []:
            info = (image.get("infoList") or [{}])[0]
            raw = image.get("url") or info.get("url") or image.get("urlDefault") or ""
            url = self._build_image_url(image, raw)
            if url:
                medias.append(MediaItem(kind="image", url=url, quality="original"))

        video = note.get("video") or {}
        video_url = self._pick_video_url(video)
        cover = ((video.get("cover") or {}).get("urlDefault")) or None
        if video_url:
            medias.insert(0, MediaItem(kind="video", url=video_url, cover=cover, quality="original"))

        if not medias:
            raise ParseError(PARSE_FAILED, "该笔记不包含图片或视频")
        media_type = self._media_type(medias)
        return ParseResponse(
            platform=Platform.XHS,
            title=title,
            author=author,
            type=media_type,
            medias=medias,
        )

    @staticmethod
    def _build_image_url(image: dict, raw: str) -> str:
        """优先使用 fileId 指向的原图对象并转成 JPEG（原分辨率、无水印）。"""
        file_id = image.get("fileId")
        if file_id:
            host = urlsplit(raw).hostname or "sns-img-qc.xhscdn.com"
            host = host.replace("sns-webpic", "sns-img")
            return f"https://{host}/{file_id}?imageView2/0/format/jpg"
        return clean_image_url(raw)

    @staticmethod
    def _pick_video_url(video: dict) -> Optional[str]:
        consumer = video.get("consumer") or {}
        origin_key = consumer.get("originVideoKey") or consumer.get("origin_video_key") or ""
        if origin_key:
            return f"https://sns-video-bd.xhscdn.com/{origin_key}"
        media = video.get("media") or {}
        h264 = ((media.get("stream") or {}).get("h264")) or []
        if h264:
            master = h264[0].get("masterUrl") or h264[0].get("backupUrls") or []
            if isinstance(master, list):
                return master[0] if master else None
            return master or None
        video_info = video.get("video") or {}
        return video_info.get("url") or video.get("url") or None

    @staticmethod
    def _media_type(medias: List[MediaItem]) -> str:
        has_video = any(m.kind.value == "video" for m in medias)
        has_image = any(m.kind.value == "image" for m in medias)
        if has_video and has_image:
            return "mixed"
        if has_video:
            return "video"
        return "image"

