import json

import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.douyin import DouyinParser


def _router_html(state: dict) -> str:
    return f'<html><script>window._ROUTER_DATA={json.dumps(state)}</script></html>'


@pytest.mark.asyncio
async def test_douyin_video():
    content_id = "7123456789012345678"
    final_url = f"https://www.douyin.com/video/{content_id}"
    state = {
        "loaderData": {
            "video_(id)/page": {
                "videoInfoRes": {
                    "item_list": [
                        {
                            "desc": "测试视频",
                            "author": {"nickname": "抖音作者"},
                            "video": {
                                "play_addr": {"uri": "v0300f10000abc", "url_list": ["https://www.douyin.com/aweme/v1/play/?video_id=v0300f10000abc"]},
                                "cover": {"url_list": ["https://p3.douyinpic.com/cover.jpg"]},
                            },
                        }
                    ]
                }
            }
        }
    }
    with respx.mock:
        respx.get("https://v.douyin.com/iAbCdEf/").mock(
            return_value=httpx.Response(302, headers={"Location": final_url})
        )
        respx.get(final_url).mock(return_value=httpx.Response(200, text="<html>redirect target</html>"))
        respx.get(f"https://m.douyin.com/share/video/{content_id}/").mock(
            return_value=httpx.Response(200, text=_router_html(state))
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await DouyinParser(client).parse("https://v.douyin.com/iAbCdEf/")

    assert result.platform == Platform.DOUYIN
    assert result.type == "video"
    assert result.medias[0].url == "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300f10000abc&ratio=1080p&line=0"
    assert result.medias[0].cover == "https://p3.douyinpic.com/cover.jpg"


@pytest.mark.asyncio
async def test_douyin_note_images_via_video_info_res():
    content_id = "7668346756942644842"
    final_url = f"https://www.douyin.com/note/{content_id}"
    state = {
        "loaderData": {
            "note_(id)/page": {
                "videoInfoRes": {
                    "item_list": [
                        {
                            "desc": "",
                            "author": {"nickname": "图文作者"},
                            "images": [
                                {"url_list": ["https://p3-sign.douyinpic.com/img1.jpeg"]},
                                {"url_list": ["https://p3-sign.douyinpic.com/img2.jpeg"]},
                            ],
                            "video": {
                                "play_addr": {"uri": "https://sf6-cdn-tos.douyinstatic.com/obj/video.mp4"},
                                "cover": {"url_list": ["https://p3.douyinpic.com/cover.jpg"]},
                            },
                        }
                    ]
                }
            }
        }
    }
    with respx.mock:
        respx.get("https://v.douyin.com/iGhIjKl/").mock(
            return_value=httpx.Response(302, headers={"Location": final_url})
        )
        respx.get(final_url).mock(return_value=httpx.Response(200, text="<html>redirect target</html>"))
        respx.get(f"https://m.douyin.com/share/note/{content_id}/").mock(
            return_value=httpx.Response(200, text=_router_html(state))
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await DouyinParser(client).parse("https://v.douyin.com/iGhIjKl/")

    assert result.type == "mixed"
    assert len(result.medias) == 3
    assert result.medias[0].url == "https://sf6-cdn-tos.douyinstatic.com/obj/video.mp4"
    assert [m.url for m in result.medias[1:]] == [
        "https://p3-sign.douyinpic.com/img1.jpeg",
        "https://p3-sign.douyinpic.com/img2.jpeg",
    ]


@pytest.mark.asyncio
async def test_douyin_fallback_iteminfo():
    content_id = "7123456789012345680"
    final_url = f"https://www.douyin.com/video/{content_id}"
    with respx.mock:
        respx.get("https://v.douyin.com/iFallback/").mock(
            return_value=httpx.Response(302, headers={"Location": final_url})
        )
        respx.get(final_url).mock(return_value=httpx.Response(200, text="<html>redirect target</html>"))
        respx.get(f"https://m.douyin.com/share/video/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(f"https://www.iesdouyin.com/share/video/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(
            f"https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={content_id}"
        ).mock(
            return_value=httpx.Response(
                200,
                json={
                    "aweme_detail": None,
                    "filter_detail": {"filter_reason": "images_base"},
                    "status_code": 0,
                },
            )
        )
        respx.get(
            f"https://m.douyin.com/web/api/v2/aweme/iteminfo/?item_ids={content_id}"
        ).mock(
            return_value=httpx.Response(
                200,
                json={
                    "item_list": [
                        {
                            "desc": "回退视频",
                            "author": {"nickname": "作者"},
                            "video": {"play_addr": {"uri": "v0300fbbbb", "url_list": []}},
                        }
                    ]
                },
            )
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await DouyinParser(client).parse("https://v.douyin.com/iFallback/")

    assert result.title == "回退视频"
    assert result.medias[0].url == "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0300fbbbb&ratio=1080p&line=0"


@pytest.mark.asyncio
async def test_douyin_video_fallback_pc_detail():
    content_id = "7123456789012345678"
    final_url = f"https://www.douyin.com/video/{content_id}"
    with respx.mock:
        respx.get("https://v.douyin.com/iPcDetail/").mock(
            return_value=httpx.Response(302, headers={"Location": final_url})
        )
        respx.get(final_url).mock(return_value=httpx.Response(200, text="<html>redirect target</html>"))
        respx.get(f"https://m.douyin.com/share/video/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(f"https://www.iesdouyin.com/share/video/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(
            f"https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={content_id}"
        ).mock(
            return_value=httpx.Response(
                200,
                json={
                    "status_code": 0,
                    "aweme_detail": {
                        "desc": "PC详情视频",
                        "author": {"nickname": "视频作者"},
                        "video": {
                            "play_addr": {"uri": "v0200pc00001", "url_list": []},
                            "cover": {"url_list": ["https://p3.douyinpic.com/pc_cover.jpg"]},
                        },
                    },
                },
            )
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await DouyinParser(client).parse("https://v.douyin.com/iPcDetail/")

    assert result.type == "video"
    assert result.title == "PC详情视频"
    assert result.author == "视频作者"
    assert result.medias[0].url == (
        "https://aweme.snssdk.com/aweme/v1/play/?video_id=v0200pc00001&ratio=1080p&line=0"
    )
    assert result.medias[0].cover == "https://p3.douyinpic.com/pc_cover.jpg"


@pytest.mark.asyncio
async def test_douyin_note_fallback_seo_ld_json():
    content_id = "7668346756942644842"
    final_url = f"https://www.douyin.com/note/{content_id}"
    ld_html = (
        '<html><script type="application/ld+json">'
        + json.dumps(
            {
                "@context": "https://schema.org",
                "@type": "article",
                "headline": "图",
                "articleBody": "图文笔记标题",
                "image": [
                    "https://p3-pc-sign.douyinpic.com/img1.jpeg",
                    "https://p3-pc-sign.douyinpic.com/img2.jpeg",
                ],
                "author": {"@type": "Person", "name": "图文作者"},
            }
        )
        + "</script></html>"
    )
    with respx.mock:
        respx.get("https://v.douyin.com/iSeoNote/").mock(
            return_value=httpx.Response(302, headers={"Location": final_url})
        )
        respx.get(final_url).mock(return_value=httpx.Response(200, text="<html>redirect target</html>"))
        respx.get(f"https://www.iesdouyin.com/share/note/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(f"https://m.douyin.com/share/slides/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(f"https://www.iesdouyin.com/share/slides/{content_id}/").mock(
            return_value=httpx.Response(200, text="<html>no router data</html>")
        )
        respx.get(
            f"https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={content_id}"
        ).mock(
            return_value=httpx.Response(
                200,
                json={
                    "aweme_detail": None,
                    "filter_detail": {"filter_reason": "images_base"},
                    "status_code": 0,
                },
            )
        )
        respx.get(f"https://m.douyin.com/share/note/{content_id}/").mock(
            return_value=httpx.Response(200, text=ld_html)
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await DouyinParser(client).parse("https://v.douyin.com/iSeoNote/")

    assert result.type == "image"
    assert result.title == "图文笔记标题"
    assert result.author == "图文作者"
    assert [m.url for m in result.medias] == [
        "https://p3-pc-sign.douyinpic.com/img1.jpeg",
        "https://p3-pc-sign.douyinpic.com/img2.jpeg",
    ]

