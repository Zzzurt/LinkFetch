"""FastAPI 入口：/api/parse 解析接口 + /api/health 健康检查。"""
from __future__ import annotations

import logging
from typing import Optional

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .config import Settings
from .models import ErrorResponse, ParseError, ParseRequest, ParseResponse
from .parsers import get_parser_class
from .platform import detect_platform

logger = logging.getLogger("linkfetch")

app = FastAPI(title="LinkFetch 解析服务", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

settings = Settings()

_COOKIE_HEADERS = {
    "xhs": ("x_cookie_xhs", "XHS_COOKIE"),
    "douyin": ("x_cookie_douyin", "DOUYIN_COOKIE"),
    "weibo": ("x_cookie_weibo", "WEIBO_COOKIE"),
}


@app.exception_handler(ParseError)
async def parse_error_handler(request: Request, exc: ParseError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.http_status,
        content=ErrorResponse(code=exc.code, message=exc.message).model_dump(),
    )


@app.get("/api/health")
async def health() -> dict:
    return {"status": "ok", "service": "linkfetch"}


@app.post("/api/parse", response_model=ParseResponse)
async def parse(
    body: ParseRequest,
    x_api_token: Optional[str] = Header(default=None),
    x_cookie_xhs: Optional[str] = Header(default=None),
    x_cookie_douyin: Optional[str] = Header(default=None),
    x_cookie_weibo: Optional[str] = Header(default=None),
) -> ParseResponse:
    if settings.api_token and x_api_token != settings.api_token:
        raise HTTPException(status_code=401, detail="无效的 API Token")

    platform = detect_platform(body.url)
    parser_class = get_parser_class(platform)
    cookie = _pick_cookie(platform.value, x_cookie_xhs, x_cookie_douyin, x_cookie_weibo)

    try:
        timeout = httpx.Timeout(20.0, connect=10.0)
        async with httpx.AsyncClient(
            timeout=timeout,
            follow_redirects=True,
            headers={"User-Agent": "LinkFetch/1.0"},
        ) as client:
            parser = parser_class(client)
            return await parser.parse(body.url, cookie=cookie)
    except ParseError:
        raise
    except httpx.TimeoutException as exc:
        raise ParseError("parse_failed", "请求平台超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        raise ParseError("parse_failed", f"请求平台失败：{exc}") from exc
    except Exception as exc:  # noqa: BLE001 - 兜底防止内部错误泄漏
        logger.exception("解析过程发生未预期异常")
        raise ParseError("parse_failed", f"解析失败：{exc}") from exc


def _pick_cookie(
    platform: str,
    x_cookie_xhs: Optional[str],
    x_cookie_douyin: Optional[str],
    x_cookie_weibo: Optional[str],
) -> Optional[str]:
    header_map = {
        "xhs": x_cookie_xhs,
        "douyin": x_cookie_douyin,
        "weibo": x_cookie_weibo,
    }
    header_cookie = header_map.get(platform)
    if header_cookie:
        return header_cookie
    env_map = {"xhs": settings.xhs_cookie, "douyin": settings.douyin_cookie, "weibo": settings.weibo_cookie}
    return env_map.get(platform) or None

