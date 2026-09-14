from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from app.models import MediaItem, ParseResponse, Platform
import pytest


class FakeParser:
    def __init__(self, client):
        self.client = client

    async def parse(self, url, cookie=None):
        return ParseResponse(
            platform=Platform.XHS,
            title="测试笔记",
            author="作者",
            type="image",
            medias=[MediaItem(kind="image", url="https://cdn.example.com/a.jpg", quality="original")],
        )


@pytest.fixture
def client():
    # 必须用上下文管理器：只有进入 with 才会执行 lifespan，app.state.http 才会被初始化
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def open_mode(monkeypatch):
    """模拟「未配置 API_TOKEN 且显式放开」的本机调试模式，避开鉴权直达解析逻辑。"""
    monkeypatch.setattr(main_module.settings, "api_token", None)
    monkeypatch.setattr(main_module.settings, "allow_unauthenticated", True)
    monkeypatch.setattr(main_module, "get_parser_class", lambda platform: FakeParser)


def test_health(client):
    resp = client.get("/api/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_parse_rejects_when_token_not_configured(client, monkeypatch):
    """默认拒绝：未配置 API_TOKEN 且未显式放开时，任何解析请求都不得放行。"""
    monkeypatch.setattr(main_module.settings, "api_token", None)
    monkeypatch.setattr(main_module.settings, "allow_unauthenticated", False)

    resp = client.post("/api/parse", json={"url": "https://xhslink.com/a/x"})

    assert resp.status_code == 503


def test_parse_success(client, open_mode):
    resp = client.post("/api/parse", json={"url": "https://xhslink.com/a/x"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["platform"] == "xhs"
    assert body["type"] == "image"
    assert body["medias"][0]["url"].startswith("https://")


def test_parse_unsupported(client, open_mode):
    resp = client.post("/api/parse", json={"url": "https://example.com/video"})
    assert resp.status_code == 400
    assert resp.json()["code"] == "unsupported_link"


def test_parse_empty_url(client):
    # 请求体校验（422）在进入处理函数之前完成，因此不需要鉴权配置
    resp = client.post("/api/parse", json={"url": ""})
    assert resp.status_code == 422


def test_auth_required(client, monkeypatch):
    monkeypatch.setattr(main_module, "get_parser_class", lambda platform: FakeParser)
    monkeypatch.setattr(main_module.settings, "api_token", "secret")
    monkeypatch.setattr(main_module.settings, "allow_unauthenticated", False)

    assert client.post("/api/parse", json={"url": "https://xhslink.com/a/x"}).status_code == 401
    resp = client.post(
        "/api/parse",
        json={"url": "https://xhslink.com/a/x"},
        headers={"X-API-Token": "secret"},
    )
    assert resp.status_code == 200


def test_error_response_does_not_leak_internals(client, monkeypatch):
    """内部异常不得回传堆栈或库细节，只给可关联的追踪号。"""
    monkeypatch.setattr(main_module.settings, "api_token", None)
    monkeypatch.setattr(main_module.settings, "allow_unauthenticated", True)

    class ExplodingParser:
        def __init__(self, client):
            self.client = client

        async def parse(self, url, cookie=None):
            raise RuntimeError("secret-internal-detail at /opt/app/parsers/xhs.py:42")

    monkeypatch.setattr(main_module, "get_parser_class", lambda platform: ExplodingParser)

    resp = client.post("/api/parse", json={"url": "https://xhslink.com/a/x"})

    assert resp.status_code == 502
    message = resp.json()["message"]
    assert "secret-internal-detail" not in message
    assert "xhs.py" not in message
    assert "追踪号" in message
