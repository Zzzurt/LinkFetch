"""服务配置：从环境变量读取。"""
from __future__ import annotations

import os
from typing import Optional


class Settings:
    def __init__(self) -> None:
        self.api_token: Optional[str] = os.getenv("API_TOKEN") or None
        self.xhs_cookie: str = os.getenv("XHS_COOKIE") or ""
        self.douyin_cookie: str = os.getenv("DOUYIN_COOKIE") or ""
        self.weibo_cookie: str = os.getenv("WEIBO_COOKIE") or ""

