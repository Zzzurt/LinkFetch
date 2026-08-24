import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.x import XParser


@pytest.mark.asyncio
async def test_x_photo_tweet():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "一张照片",
                    "user": {"name": "作者", "screen_name": "author"},
                    "mediaDetails": [
                        {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/AbC.jpg"},
                        {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/DeF_large.jpg"},
                    ],
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/2083053411524850111")

    assert result.platform == Platform.X
    assert result.title == "一张照片"
    assert result.type == "image"
    images = [m for m in result.medias if m.kind.value == "image"]
    assert images[0].url == "https://pbs.twimg.com/media/AbC.jpg?name=orig"
    assert images[1].url == "https://pbs.twimg.com/media/DeF.jpg?name=orig"


@pytest.mark.asyncio
async def test_x_video_picks_highest_bitrate():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "视频",
                    "mediaDetails": [
                        {
                            "type": "video",
                            "media_url_https": "https://pbs.twimg.com/cover.jpg",
                            "video_info": {
                                "variants": [
                                    {"bitrate": 832000, "content_type": "video/mp4", "url": "https://video.twimg.com/low.mp4"},
                                    {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/master.m3u8"},
                                    {"bitrate": 2176000, "content_type": "video/mp4", "url": "https://video.twimg.com/high.mp4"},
                                ]
                            },
                        }
                    ],
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/2082841167251845607")

    assert result.type == "video"
    assert result.medias[0].url == "https://video.twimg.com/high.mp4"
    assert result.medias[0].quality == "hd"


@pytest.mark.asyncio
async def test_x_hls_fallback():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "HLS",
                    "mediaDetails": [
                        {
                            "type": "video",
                            "media_url_https": "https://pbs.twimg.com/cover.jpg",
                            "video_info": {
                                "variants": [
                                    {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/master.m3u8"}
                                ]
                            },
                        }
                    ],
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://twitter.com/user/status/1")

    assert result.medias[0].url == "https://video.twimg.com/master.m3u8"


@pytest.mark.asyncio
async def test_x_tco_redirect():
    tweet_id = "2083053411524850111"
    with respx.mock:
        respx.get("https://t.co/xyz123").mock(
            return_value=httpx.Response(302, headers={"Location": f"https://x.com/i/status/{tweet_id}"})
        )
        respx.get(f"https://x.com/i/status/{tweet_id}").mock(return_value=httpx.Response(200, text="redirect target"))
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "短链",
                    "mediaDetails": [{"type": "photo", "media_url_https": "https://pbs.twimg.com/media/s.jpg"}],
                },
            )
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await XParser(client).parse("https://t.co/xyz123")

    assert result.title == "短链"
    assert result.medias[0].url == "https://pbs.twimg.com/media/s.jpg?name=orig"


@pytest.mark.asyncio
async def test_x_deleted_tweet():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(return_value=httpx.Response(404))
        async with httpx.AsyncClient() as client:
            try:
                await XParser(client).parse("https://x.com/i/status/9")
            except Exception as exc:  # noqa: BLE001
                assert getattr(exc, "code", None) == "parse_failed"
            else:
                raise AssertionError("应当抛出 ParseError")


@pytest.mark.asyncio
async def test_x_invalid_link_without_network():
    async with httpx.AsyncClient() as client:
        try:
            await XParser(client).parse("https://x.com/home")
        except Exception as exc:  # noqa: BLE001
            assert getattr(exc, "code", None) == "parse_failed"
        else:
            raise AssertionError("应当抛出 ParseError")
