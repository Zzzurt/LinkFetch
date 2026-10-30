"""受限 HTTP 抓取：逐跳校验重定向目标，并限制响应体大小。"""
from __future__ import annotations

import asyncio
import ipaddress
import json
import socket
from dataclasses import dataclass
from typing import Any, Mapping, Optional, Tuple
from urllib.parse import urljoin, urlsplit

import httpx

from .models import PARSE_FAILED, ParseError
from .platform import is_platform_host

MAX_RESPONSE_BYTES = 8 * 1024 * 1024
MAX_REDIRECTS = 5
_ALLOWED_SCHEMES = ("http", "https")


@dataclass(frozen=True)
class FetchedResponse:
    status_code: int
    url: str
    history: Tuple[str, ...]
    text: str
    headers: Mapping[str, str]

    def json(self) -> Any:
        return json.loads(self.text)


def _blocked_address(address: str) -> bool:
    try:
        ip = ipaddress.ip_address(address)
    except ValueError:
        return False
    # is_global 排除私网、环回、链路本地、保留与多播地址
    return not ip.is_global


def _resolve_host(host: str) -> Tuple[str, ...]:
    try:
        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except OSError:
        # 解析失败时交给 HTTP 客户端自然报错，不在这里伪装成安全校验失败
        return ()
    return tuple({info[4][0] for info in infos})


async def ensure_public_url(url: str) -> None:
    """拒绝非 http(s)、私网、环回与链路本地目标（含每一跳重定向）。"""
    parsed = urlsplit(url)
    if parsed.scheme.lower() not in _ALLOWED_SCHEMES:
        raise ParseError(PARSE_FAILED, "链接跳转到了不允许的地址", http_status=400)
    host = parsed.hostname
    if not host:
        raise ParseError(PARSE_FAILED, "链接缺少有效域名", http_status=400)

    try:
        literal = ipaddress.ip_address(host)
    except ValueError:
        addresses = await asyncio.to_thread(_resolve_host, host)
    else:
        addresses = (str(literal),)

    if any(_blocked_address(address) for address in addresses):
        raise ParseError(PARSE_FAILED, "链接跳转到了不允许的地址", http_status=400)


async def _read_limited(response: httpx.Response, max_bytes: int) -> str:
    content = bytearray()
    async for chunk in response.aiter_bytes():
        if len(content) + len(chunk) > max_bytes:
            raise ParseError(PARSE_FAILED, "平台响应过大，已中止解析")
        content.extend(chunk)
    encoding = response.charset_encoding or "utf-8"
    return bytes(content).decode(encoding, errors="replace")


async def fetch(
    client: httpx.AsyncClient,
    url: str,
    *,
    headers: Optional[Mapping[str, str]] = None,
    max_redirects: int = MAX_REDIRECTS,
    max_bytes: int = MAX_RESPONSE_BYTES,
) -> FetchedResponse:
    """手动跟随重定向，每一跳都做目标校验；响应体超过上限即中止。"""
    current = url
    current_headers = dict(headers) if headers else None
    platform_cookie = None
    if current_headers:
        platform_cookie = current_headers.get("Cookie") or current_headers.get("cookie")
    history: list[str] = []
    for _ in range(max_redirects + 1):
        await ensure_public_url(current)
        async with client.stream(
            "GET",
            current,
            headers=current_headers,
            follow_redirects=False,
        ) as response:
            if response.is_redirect:
                location = response.headers.get("Location")
                history.append(str(response.url))
                if not location:
                    return FetchedResponse(
                        status_code=response.status_code,
                        url=str(response.url),
                        history=tuple(history),
                        text="",
                        headers=dict(response.headers),
                    )
                current = urljoin(str(response.url), location)
                # 平台 Cookie 只发往平台域名：短链可以跨站跳转，但凭证不能跟着去第三方；
                # 若后续又跳回平台域名，再把凭证带回去（登录态不因中转域名丢失）
                if current_headers:
                    if is_platform_host(urlsplit(current).hostname or ""):
                        if platform_cookie:
                            current_headers["Cookie"] = platform_cookie
                    else:
                        current_headers.pop("Cookie", None)
                        current_headers.pop("cookie", None)
                continue
            text = await _read_limited(response, max_bytes)
            return FetchedResponse(
                status_code=response.status_code,
                url=str(response.url),
                history=tuple(history),
                text=text,
                headers=dict(response.headers),
            )
    raise ParseError(PARSE_FAILED, "链接跳转次数过多", http_status=400)
