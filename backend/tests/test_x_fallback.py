import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.x import XParser


TOMBSTONE = {"__typename": "TweetTombstone", "tombstone": {}}


@pytest.mark.asyncio
async def test_x_tombstone_falls_back_to_vxtwitter():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(200, json=TOMBSTONE)
        )
        respx.get("https://api.vxtwitter.com/i/status/2082841167251845607").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "长视频 via vxtwitter",
                    "user_name": "作者",
                    "user_screen_name": "author",
                    "media_extended": [
                        {
                            "type": "video",
                            "url": "https://video.twimg.com/vx_full.mp4",
                            "thumbnail_url": "https://pbs.twimg.com/vx_thumb.jpg",
                        },
                        {"type": "image", "url": "https://pbs.twimg.com/vx_img.jpg"},
                    ],
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/2082841167251845607")

    assert result.platform == Platform.X
    assert result.type == "mixed"
    assert result.title == "长视频 via vxtwitter"
    assert result.author == "作者"
    assert result.medias[0].kind.value == "video"
    assert result.medias[0].url == "https://video.twimg.com/vx_full.mp4"
    assert result.medias[0].cover == "https://pbs.twimg.com/vx_thumb.jpg"
    assert result.medias[1].kind.value == "image"
    assert result.medias[1].url == "https://pbs.twimg.com/vx_img.jpg"


@pytest.mark.asyncio
async def test_x_tombstone_falls_back_to_fxtwitter_when_vx_fails():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(200, json=TOMBSTONE)
        )
        respx.get("https://api.vxtwitter.com/i/status/1").mock(
            return_value=httpx.Response(404, json={"code": 404, "message": "no tweet found"})
        )
        respx.get("https://api.fxtwitter.com/status/1").mock(
            return_value=httpx.Response(
                200,
                json={
                    "code": 200,
                    "message": "ok",
                    "tweet": {
                        "text": "长视频 via fxtwitter",
                        "author": {"name": "作者", "screen_name": "author"},
                        "media": {
                            "photos": [{"url": "https://pbs.twimg.com/fx_img.jpg"}],
                            "videos": [
                                {
                                    "url": "https://video.twimg.com/fx.m3u8",
                                    "thumbnailUrl": "https://pbs.twimg.com/fx_thumb.jpg",
                                }
                            ],
                        },
                    },
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/1")

    assert result.type == "mixed"
    assert result.medias[0].kind.value == "image"
    assert result.medias[0].url == "https://pbs.twimg.com/fx_img.jpg"
    assert result.medias[1].kind.value == "video"
    assert result.medias[1].url == "https://video.twimg.com/fx.m3u8"
    assert result.medias[1].cover == "https://pbs.twimg.com/fx_thumb.jpg"


@pytest.mark.asyncio
async def test_x_all_fallbacks_fail_raises_parse_failed():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(200, json=TOMBSTONE)
        )
        respx.get("https://api.vxtwitter.com/i/status/2").mock(return_value=httpx.Response(404))
        respx.get("https://api.fxtwitter.com/status/2").mock(
            return_value=httpx.Response(
                200, json={"code": 404, "message": "Tweet not found", "tweet": None}
            )
        )
        async with httpx.AsyncClient() as client:
            try:
                await XParser(client).parse("https://x.com/i/status/2")
            except Exception as exc:  # noqa: BLE001
                assert getattr(exc, "code", None) == "parse_failed"
                assert "该推文不包含图片或视频" in str(exc)
            else:
                raise AssertionError("应当抛出 ParseError")
