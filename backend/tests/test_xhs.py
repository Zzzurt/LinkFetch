import json

import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.xhs import XiaohongshuParser

SAMPLE_NOTE = {
    "title": "测试笔记",
    "type": "video",
    "user": {"nickName": "测试作者"},
    "imageList": [
        {"fileId": "file001", "url": "https://sns-webpic-qc.xhscdn.com/2026/01/file001!h5_1080jpg"},
        {"fileId": "file002", "url": "https://sns-webpic-qc.xhscdn.com/2026/01/file002!h5_1080jpg"},
    ],
    "video": {
        "consumer": {"originVideoKey": "video_key_123"},
        "cover": {"urlDefault": "https://sns-webpic-h.xhscdn.com/cover.jpg!nd_dft_wl_watermark_webp"},
    },
}


def _explore_html(note_id: str) -> str:
    state = {"noteData": {"data": {"noteData": SAMPLE_NOTE}}}
    return f'<html><script>window.__INITIAL_STATE__={json.dumps(state)}</script></html>'


@pytest.mark.asyncio
async def test_xhs_mixed_video_and_images():
    short = "https://xhslink.com/a/AbC12"
    explore = "https://www.xiaohongshu.com/explore/64abc123def4567890123456"
    with respx.mock:
        respx.get(short).mock(
            return_value=httpx.Response(302, headers={"Location": explore + "?xsec_token=TOK&xsec_source=pc_feed"})
        )
        respx.get(explore).mock(return_value=httpx.Response(200, text=_explore_html("64abc123def4567890123456")))
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await XiaohongshuParser(client).parse(short)

    assert result.platform == Platform.XHS
    assert result.title == "测试笔记"
    assert result.author == "测试作者"
    assert result.type == "mixed"
    assert len(result.medias) == 3
    assert result.medias[0].kind.value == "video"
    assert result.medias[0].url == "https://sns-video-bd.xhscdn.com/video_key_123"
    assert [m.url for m in result.medias[1:]] == [
        "https://sns-img-qc.xhscdn.com/file001?imageView2/0/format/jpg",
        "https://sns-img-qc.xhscdn.com/file002?imageView2/0/format/jpg",
    ]


@pytest.mark.asyncio
async def test_xhs_image_only():
    note = dict(SAMPLE_NOTE)
    note.pop("video")
    state = {"noteData": {"data": {"noteData": note}}}
    html = f'<html><script>window.__INITIAL_STATE__={json.dumps(state)}</script></html>'
    with respx.mock:
        respx.get("https://xhslink.com/a/Img").mock(
            return_value=httpx.Response(302, headers={"Location": "https://www.xiaohongshu.com/explore/64abc123def4567890123456"})
        )
        respx.get("https://www.xiaohongshu.com/explore/64abc123def4567890123456").mock(
            return_value=httpx.Response(200, text=html)
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await XiaohongshuParser(client).parse("https://xhslink.com/a/Img")
    assert result.type == "image"
    assert len(result.medias) == 2


@pytest.mark.asyncio
async def test_xhs_parse_failed_when_page_changes():
    with respx.mock:
        respx.get("https://xhslink.com/a/Bad").mock(
            return_value=httpx.Response(302, headers={"Location": "https://www.xiaohongshu.com/explore/64abc123def4567890123456"})
        )
        respx.get("https://www.xiaohongshu.com/explore/64abc123def4567890123456").mock(
            return_value=httpx.Response(200, text="<html>no state</html>")
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            try:
                await XiaohongshuParser(client).parse("https://xhslink.com/a/Bad")
            except Exception as exc:  # noqa: BLE001
                assert getattr(exc, "code", None) == "parse_failed"
            else:
                raise AssertionError("应当抛出 ParseError")

