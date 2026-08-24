"""平台解析器注册表。"""
from __future__ import annotations

from typing import Dict, Type

from ..models import Platform
from .base import BaseParser
from .douyin import DouyinParser
from .weibo import WeiboParser
from .x import XParser
from .xhs import XiaohongshuParser

PARSERS: Dict[Platform, Type[BaseParser]] = {
    Platform.XHS: XiaohongshuParser,
    Platform.DOUYIN: DouyinParser,
    Platform.WEIBO: WeiboParser,
    Platform.X: XParser,
}


def get_parser_class(platform: Platform) -> Type[BaseParser]:
    return PARSERS[platform]

