"""解析器基类：所有平台解析器实现统一接口。"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Mapping, Optional

import httpx

from ..models import ParseResponse, Platform
from ..net import FetchedResponse, fetch


class BaseParser(ABC):
    platform: Platform

    def __init__(self, client: httpx.AsyncClient) -> None:
        self.client = client

    async def fetch(
        self, url: str, *, headers: Optional[Mapping[str, str]] = None
    ) -> FetchedResponse:
        """所有平台请求都走受限抓取：逐跳校验重定向目标并限制响应体大小。"""
        return await fetch(self.client, url, headers=headers)

    @abstractmethod
    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        """解析链接，返回无水印媒体列表。"""
        raise NotImplementedError

