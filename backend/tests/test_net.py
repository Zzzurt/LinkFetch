import httpx
import pytest
import respx

from app.models import ParseError
from app.net import ensure_public_url, fetch


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "url",
    [
        "http://127.0.0.1/",
        "http://10.0.0.5/",
        "http://169.254.169.254/latest/meta-data",
        "http://[::1]/",
        "ftp://example.com/file",
    ],
)
async def test_ensure_public_url_blocks_private_and_non_http(url):
    with pytest.raises(ParseError):
        await ensure_public_url(url)


@pytest.mark.asyncio
async def test_ensure_public_url_blocks_hostname_resolving_to_private(monkeypatch):
    monkeypatch.setattr("app.net._resolve_host", lambda host: ("192.168.1.10",))
    with pytest.raises(ParseError):
        await ensure_public_url("http://internal.example/")


@pytest.mark.asyncio
async def test_ensure_public_url_allows_public_ip():
    await ensure_public_url("http://93.184.216.34/")


@pytest.mark.asyncio
async def test_fetch_follows_public_redirect():
    async with httpx.AsyncClient() as client:
        with respx.mock:
            respx.get("http://93.184.216.34/start").mock(
                return_value=httpx.Response(302, headers={"Location": "/final"})
            )
            respx.get("http://93.184.216.34/final").mock(
                return_value=httpx.Response(200, text="ok")
            )

            response = await fetch(client, "http://93.184.216.34/start")

    assert response.text == "ok"
    assert response.url == "http://93.184.216.34/final"
    assert response.history == ("http://93.184.216.34/start",)


@pytest.mark.asyncio
async def test_fetch_rejects_redirect_to_private_address():
    async with httpx.AsyncClient() as client:
        with respx.mock:
            respx.get("http://93.184.216.34/start").mock(
                return_value=httpx.Response(
                    302,
                    headers={"Location": "http://169.254.169.254/latest/meta-data"},
                )
            )

            with pytest.raises(ParseError):
                await fetch(client, "http://93.184.216.34/start")

            # 只请求了初始公开地址，没有跟到私网重定向目标
            assert respx.calls.call_count == 1


@pytest.mark.asyncio
async def test_fetch_drops_cookie_on_cross_site_redirect():
    async with httpx.AsyncClient() as client:
        with respx.mock:
            respx.get("http://93.184.216.34/start").mock(
                return_value=httpx.Response(302, headers={"Location": "http://8.8.8.8/final"})
            )
            respx.get("http://8.8.8.8/final").mock(
                return_value=httpx.Response(200, text="ok")
            )

            await fetch(client, "http://93.184.216.34/start", headers={"Cookie": "secret"})

            # 第一跳带 Cookie，跳到非平台域名后必须丢弃
            assert respx.calls[0].request.headers.get("cookie") == "secret"
            assert respx.calls[1].request.headers.get("cookie") is None


@pytest.mark.asyncio
async def test_fetch_rejects_oversized_body():
    async with httpx.AsyncClient() as client:
        with respx.mock:
            respx.get("http://93.184.216.34/big").mock(
                return_value=httpx.Response(200, content=b"x" * 32)
            )

            with pytest.raises(ParseError):
                await fetch(client, "http://93.184.216.34/big", max_bytes=16)
