"""FastAPI 入口：/api/parse 解析接口 + /api/health 健康检查。"""
from __future__ import annotations

import hmac
import logging
import uuid
from contextlib import asynccontextmanager
from typing import AsyncIterator, Optional

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .config import Settings
from .models import ErrorResponse, ParseError, ParseRequest, ParseResponse
from .parsers import get_parser_class
from .platform import detect_platform

logger = logging.getLogger("linkfetch")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """
    复用同一个 HTTP 客户端：连接池与 TLS 会话可跨请求复用。
    原先每个请求都新建 AsyncClient，等于每次都重新握手，并发稍高即成为瓶颈。
    """
    app.state.http = httpx.AsyncClient(
        timeout=httpx.Timeout(20.0, connect=10.0),
        follow_redirects=True,
        headers={"User-Agent": "LinkFetch/1.0"},
        limits=httpx.Limits(max_connections=50, max_keepalive_connections=20),
    )
    try:
        yield
    finally:
        await app.state.http.aclose()


app = FastAPI(title="LinkFetch 解析服务", version="1.0.0", lifespan=lifespan)

settings = Settings()

# 默认不开放跨域：移动端不依赖同源策略，开放 CORS 只会让任意网页把本服务当免费代理。
if settings.cors_allow_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allow_origins,
        allow_methods=["POST", "GET"],
        allow_headers=[
            "Content-Type",
            "X-API-Token",
            "X-Cookie-XHS",
            "X-Cookie-DOUYIN",
            "X-Cookie-WEIBO",
        ],
    )


def _authorize(provided_token: Optional[str]) -> None:
    """
    默认拒绝：未配置 API_TOKEN 且未显式放开时，拒绝所有解析请求。

    该接口可携带任意 X-Cookie-* 头代发请求，一旦无鉴权暴露在公网，
    等同把服务器变成开放代理，风控与封禁成本都由部署者承担。
    """
    expected = settings.api_token
    if not expected:
        if settings.allow_unauthenticated:
            logger.warning("未配置 API_TOKEN 且已放开鉴权，服务处于无认证状态")
            return
        raise HTTPException(
            status_code=503,
            detail="服务未配置鉴权：请设置 API_TOKEN 环境变量（本机调试可设 ALLOW_UNAUTHENTICATED=1）",
        )
    # 常量时间比较，避免按字节短路带来的时序侧信道
    if not hmac.compare_digest(provided_token or "", expected):
        raise HTTPException(status_code=401, detail="无效的 API Token")


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
    request: Request,
    x_api_token: Optional[str] = Header(default=None),
    x_cookie_xhs: Optional[str] = Header(default=None),
    x_cookie_douyin: Optional[str] = Header(default=None),
    x_cookie_weibo: Optional[str] = Header(default=None),
) -> ParseResponse:
    _authorize(x_api_token)

    platform = detect_platform(body.url)
    parser_class = get_parser_class(platform)
    cookie = _pick_cookie(platform.value, x_cookie_xhs, x_cookie_douyin, x_cookie_weibo)

    # 追踪号：内部细节只进日志，回给客户端的仅是可关联的短 ID
    trace_id = uuid.uuid4().hex[:8]
    try:
        client: httpx.AsyncClient = request.app.state.http
        parser = parser_class(client)
        return await parser.parse(body.url, cookie=cookie)
    except ParseError:
        raise
    except httpx.TimeoutException as exc:
        logger.warning("[%s] 请求平台超时: %s", trace_id, exc)
        raise ParseError("parse_failed", f"请求平台超时，请稍后重试（追踪号 {trace_id}）") from exc
    except httpx.HTTPError as exc:
        logger.warning("[%s] 请求平台失败: %s", trace_id, exc)
        raise ParseError("parse_failed", f"请求平台失败，请稍后重试（追踪号 {trace_id}）") from exc
    except Exception:  # noqa: BLE001 - 统一兜底，避免内部细节泄漏给客户端
        logger.exception("[%s] 解析过程发生未预期异常", trace_id)
        raise ParseError("parse_failed", f"解析失败，请稍后重试（追踪号 {trace_id}）") from None


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
