"""App ↔ 服务端响应契约。

Android 侧使用 kotlinx.serialization 严格解码（只忽略未知字段，不做类型转换）。
服务端一旦改名、删除字段或把可选字段改成必填，App 会直接解码失败或丢数据。
这里用共享 fixture 把两侧钉在同一份 JSON 上：
- 本文件校验服务端模型与 fixture 的字段集合；
- android/app/src/test 下的 ParseContractTest 解码同一批 fixture。
字段清单与 android/.../data/model/Models.kt 保持一致。
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models import ErrorResponse, MediaItem, ParseResponse

FIXTURES = Path(__file__).parent / "fixtures"

RESPONSE_KEYS = {"platform", "title", "author", "type", "medias"}
MEDIA_KEYS = {"kind", "url", "cover", "quality", "width", "height", "live", "liveUrl"}
ERROR_KEYS = {"code", "message"}


def fixture_files() -> list[Path]:
    files = sorted(FIXTURES.glob("parse_*.json"))
    assert len(files) == 4, f"四平台各有契约 fixture，实际找到 {[f.name for f in files]}"
    return files


def test_response_fields_are_subset_of_android_dto():
    assert set(ParseResponse.model_fields) <= RESPONSE_KEYS
    assert set(MediaItem.model_fields) <= MEDIA_KEYS
    assert set(ErrorResponse.model_fields) <= ERROR_KEYS


def test_android_dto_required_fields_stay_required():
    # Models.kt 里只有这三项 + medias 没有默认值；变成可选会静默产生空结果
    required = {name for name, field in ParseResponse.model_fields.items() if field.is_required()}
    assert required == {"platform", "title", "type", "medias"}
    assert MediaItem.model_fields["kind"].is_required()
    assert MediaItem.model_fields["url"].is_required()


@pytest.mark.parametrize("path", fixture_files(), ids=lambda p: p.stem)
def test_fixture_matches_server_model(path: Path):
    payload = json.loads(path.read_text(encoding="utf-8"))

    response = ParseResponse.model_validate(payload)

    # 回写时字段集合必须仍落在 App 能解码的范围内
    dumped = response.model_dump()
    assert set(dumped) <= RESPONSE_KEYS
    for media in dumped["medias"]:
        assert set(media) <= MEDIA_KEYS
    assert {"kind", "url"} <= set(dumped["medias"][0])


@pytest.mark.parametrize("path", fixture_files(), ids=lambda p: p.stem)
def test_fixture_is_self_consistent(path: Path):
    payload = json.loads(path.read_text(encoding="utf-8"))
    kinds = {media["kind"] for media in payload["medias"]}

    assert payload["medias"], "fixture 至少包含一个媒体"
    if kinds == {"video"}:
        assert payload["type"] == "video"
    elif kinds == {"image"}:
        assert payload["type"] == "image"
    else:
        assert payload["type"] == "mixed"


def test_unknown_field_still_validates_but_is_dropped():
    # App 侧 ignoreUnknownKeys=true：服务端若先行加字段，App 不应崩溃；
    # 该字段也不应出现在正式响应里，避免两侧长期不一致。
    payload = {
        "platform": "weibo",
        "title": "t",
        "type": "image",
        "medias": [{"kind": "image", "url": "https://example.com/a.jpg"}],
        "futureField": "ignored",
    }

    response = ParseResponse.model_validate(payload)

    assert "futureField" not in response.model_dump()


def test_missing_required_field_is_rejected():
    with pytest.raises(ValidationError):
        ParseResponse.model_validate(
            {"platform": "xhs", "type": "image", "medias": []}
        )
