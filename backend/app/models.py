"""接口数据模型与统一错误码。"""
from __future__ import annotations

from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field

# 统一错误码
UNSUPPORTED_LINK = "unsupported_link"
PARSE_FAILED = "parse_failed"
RATE_LIMITED = "rate_limited"


class Platform(str, Enum):
    XHS = "xhs"
    DOUYIN = "douyin"
    WEIBO = "weibo"
    X = "x"


class MediaKind(str, Enum):
    VIDEO = "video"
    IMAGE = "image"


class MediaItem(BaseModel):
    kind: MediaKind
    url: str
    cover: Optional[str] = None
    quality: Optional[str] = None
    width: Optional[int] = None
    height: Optional[int] = None


class ParseRequest(BaseModel):
    url: str = Field(..., min_length=1, max_length=2048, description="待解析的平台链接")


class ParseResponse(BaseModel):
    platform: Platform
    title: str
    author: Optional[str] = None
    type: str  # video | image | mixed
    medias: List[MediaItem]


class ErrorResponse(BaseModel):
    code: str
    message: str


class ParseError(Exception):
    """解析失败时抛出，由全局异常处理器转换为统一错误响应。"""

    def __init__(self, code: str, message: str, http_status: int = 502) -> None:
        self.code = code
        self.message = message
        self.http_status = http_status
        super().__init__(message)

