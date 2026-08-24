import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.x import XParser


@pytest.mark.asyncio
async def test_x_unified_card_video():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "长视频 unified_card",
                    "card": {
                        "name": "unified_card",
                        "binding_values": {
                            "unified_card": {
                                "string_value": (
                                    '{"media_entities":{"m1":{"type":"video",'
                                    '"media_url_https":"https://pbs.twimg.com/cover.jpg",'
                                    '"video_info":{"variants":['
                                    '{"bitrate":832000,"content_type":"video/mp4","url":"https://video.twimg.com/low.mp4"},'
                                    '{"content_type":"application/x-mpegURL","url":"https://video.twimg.com/master.m3u8"},'
                                    '{"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/high.mp4"}]}}}}'
                                )
                            }
                        },
                    },
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/2082841167251845607")

    assert result.platform == Platform.X
    assert result.type == "video"
    assert result.medias[0].url == "https://video.twimg.com/high.mp4"
    assert result.medias[0].cover == "https://pbs.twimg.com/cover.jpg"


@pytest.mark.asyncio
async def test_x_amplify_card_player_hls():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "amplify 长视频",
                    "card": {
                        "name": "amplify",
                        "binding_values": {
                            "player_hls_url": {"string_value": "https://video.twimg.com/amplify_v2/abc/playlist.m3u8"},
                            "player_image": {"image_value": {"url": "https://pbs.twimg.com/amplify_thumb.jpg"}},
                        },
                    },
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/1")

    assert result.type == "video"
    assert result.medias[0].url == "https://video.twimg.com/amplify_v2/abc/playlist.m3u8"
    assert result.medias[0].cover == "https://pbs.twimg.com/amplify_thumb.jpg"


@pytest.mark.asyncio
async def test_x_amplify_card_vmap():
    vmap_url = "https://video.twimg.com/amplify_v2/abc/vmap.xml"
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "amplify vmap",
                    "card": {
                        "name": "amplify",
                        "binding_values": {
                            "amplify_url_vmap": {"string_value": vmap_url},
                        },
                    },
                },
            )
        )
        respx.get(vmap_url).mock(
            return_value=httpx.Response(
                200,
                text=(
                    '<vmap:VMAP xmlns:vmap="http://www.iab.net/vmap">'
                    "<vmap:AdBreak><vmap:AdSource><vmap:AdData><vmap:VASTData>"
                    "<Ad><Creatives><Creative><Linear><MediaFiles>"
                    '<MediaFile type="video/mp4">https://video.twimg.com/low.mp4</MediaFile>'
                    '<MediaFile type="application/x-mpegURL">'
                    "<![CDATA[https://video.twimg.com/amplify_v2/abc/playlist.m3u8]]>"
                    "</MediaFile></MediaFiles></Linear></Creative></Creatives></Ad>"
                    "</vmap:VASTData></vmap:AdData></vmap:AdSource></vmap:AdBreak></vmap:VMAP>"
                ),
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/2")

    assert result.medias[0].url == "https://video.twimg.com/amplify_v2/abc/playlist.m3u8"


@pytest.mark.asyncio
async def test_x_top_level_photos_and_video():
    with respx.mock:
        respx.get("https://cdn.syndication.twimg.com/tweet-result").mock(
            return_value=httpx.Response(
                200,
                json={
                    "text": "顶层字段",
                    "photos": [
                        {"url": "https://pbs.twimg.com/media/a.jpg"},
                        {"url": "https://pbs.twimg.com/media/b_large.jpg"},
                    ],
                    "video": {
                        "poster": "https://pbs.twimg.com/poster.jpg",
                        "variants": [
                            {"bitrate": 1000000, "content_type": "video/mp4", "url": "https://video.twimg.com/v.mp4"},
                            {"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/v.m3u8"},
                        ],
                    },
                },
            )
        )
        async with httpx.AsyncClient() as client:
            result = await XParser(client).parse("https://x.com/i/status/3")

    assert result.type == "mixed"
    images = [m for m in result.medias if m.kind.value == "image"]
    videos = [m for m in result.medias if m.kind.value == "video"]
    assert images[0].url == "https://pbs.twimg.com/media/a.jpg?name=orig"
    assert images[1].url == "https://pbs.twimg.com/media/b.jpg?name=orig"
    assert videos[0].url == "https://video.twimg.com/v.mp4"
    assert videos[0].cover == "https://pbs.twimg.com/poster.jpg"
