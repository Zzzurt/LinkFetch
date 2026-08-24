"""平台识别：按域名把链接映射到对应平台。"""
from __future__ import annotations

from typing import Dict, Tuple
from urllib.parse import urlparse

from .models import UNSUPPORTED_LINK, ParseError, Platform

_HOSTS: Dict[Platform, Tuple[str, ...]] = {
    Platform.XHS: ("xhslink.com", "xiaohongshu.com", "xhslink.cn", "hongshu.com"),
    Platform.DOUYIN: ("douyin.com", "iesdouyin.com"),
    Platform.WEIBO: ("weibo.com", "weibo.cn", "m.weibo.cn", "t.cn", "video.weibo.com"),
    Platform.X: ("x.com", "twitter.com", "t.co"),
}


def detect_platform(url: str) -> Platform:
    """根据链接 host 识别平台，识别失败抛出 unsupported_link 错误。"""
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        host = ""
    if not host:
        raise ParseError(UNSUPPORTED_LINK, "无法识别的链接，请粘贴小红书 / 抖音 / 微博链接", http_status=400)
    for platform, suffixes in _HOSTS.items():
        for suffix in suffixes:
            if host == suffix or host.endswith("." + suffix):
                return platform
    raise ParseError(UNSUPPORTED_LINK, "仅支持小红书、抖音、微博平台的链接", http_status=400)

