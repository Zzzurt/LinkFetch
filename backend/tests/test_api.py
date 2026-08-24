from fastapi.testclient import TestClient

import app.main as main_module
from app.main import app
from app.models import MediaItem, ParseResponse, Platform


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


def _patch_parser(monkeypatch):
    monkeypatch.setattr(main_module, "get_parser_class", lambda platform: FakeParser)


def test_health():
    client = TestClient(app)
    resp = client.get("/api/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_parse_success(monkeypatch):
    _patch_parser(monkeypatch)
    client = TestClient(app)
    resp = client.post("/api/parse", json={"url": "https://xhslink.com/a/x"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["platform"] == "xhs"
    assert body["type"] == "image"
    assert body["medias"][0]["url"].startswith("https://")


def test_parse_unsupported():
    client = TestClient(app)
    resp = client.post("/api/parse", json={"url": "https://example.com/video"})
    assert resp.status_code == 400
    assert resp.json()["code"] == "unsupported_link"


def test_parse_empty_url():
    client = TestClient(app)
    resp = client.post("/api/parse", json={"url": ""})
    assert resp.status_code == 422


def test_auth_required(monkeypatch):
    _patch_parser(monkeypatch)
    old_settings = main_module.settings
    main_module.settings = type(
        "FakeSettings",
        (),
        {"api_token": "secret", "xhs_cookie": "", "douyin_cookie": "", "weibo_cookie": ""},
    )()
    try:
        client = TestClient(app)
        assert client.post("/api/parse", json={"url": "https://xhslink.com/a/x"}).status_code == 401
        resp = client.post(
            "/api/parse",
            json={"url": "https://xhslink.com/a/x"},
            headers={"X-API-Token": "secret"},
        )
        assert resp.status_code == 200
    finally:
        main_module.settings = old_settings

