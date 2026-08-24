"""解析器基类：所有平台解析器实现统一接口。"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Optional

import httpx

from ..models import ParseResponse, Platform


class BaseParser(ABC):
    platform: Platform

    def __init__(self, client: httpx.AsyncClient) -> None:
        self.client = client

    @abstractmethod
    async def parse(self, url: str, cookie: Optional[str] = None) -> ParseResponse:
        """解析链接，返回无水印媒体列表。"""
        raise NotImplementedError

