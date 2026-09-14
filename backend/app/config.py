"""服务配置：从环境变量读取。"""
from __future__ import annotations

import os
from typing import List, Optional


def _env_flag(name: str) -> bool:
    return (os.getenv(name) or "").strip().lower() in {"1", "true", "yes", "on"}


class Settings:
    def __init__(self) -> None:
        self.api_token: Optional[str] = os.getenv("API_TOKEN") or None
        self.xhs_cookie: str = os.getenv("XHS_COOKIE") or ""
        self.douyin_cookie: str = os.getenv("DOUYIN_COOKIE") or ""
        self.weibo_cookie: str = os.getenv("WEIBO_COOKIE") or ""

        # 默认拒绝无鉴权运行：未配置 API_TOKEN 时，除非显式放开，否则所有解析请求都被拒绝。
        self.allow_unauthenticated: bool = _env_flag("ALLOW_UNAUTHENTICATED")

        # 移动端不依赖浏览器同源策略，因此默认不开放任何跨域来源；
        # 确有网页端调用需求时，用该变量显式列出白名单（逗号分隔）。
        self.cors_allow_origins: List[str] = [
            origin.strip()
            for origin in (os.getenv("CORS_ALLOW_ORIGINS") or "").split(",")
            if origin.strip()
        ]
