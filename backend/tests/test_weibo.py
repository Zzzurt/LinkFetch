import httpx
import pytest
import respx

from app.models import Platform
from app.parsers.weibo import WeiboParser


@pytest.mark.asyncio
async def test_weibo_mixed_video_and_images():
    status_id = "AbCdEf123"
    show_data = {
        "data": {
            "text": "<p>测试微博</p>",
            "user": {"screen_name": "微博作者"},
            "pics": [
                {"url": "https://wx1.sinaimg.cn/orj360/a.jpg", "large": {"url": "https://wx1.sinaimg.cn/mw2000/a_o.jpg"}},
                {"url": "https://wx1.sinaimg.cn/orj360/b.jpg", "large": {"url": "https://wx1.sinaimg.cn/mw2000/b_o.jpg"}},
            ],
            "page_info": {
                "page_pic": "https://wx4.sinaimg.cn/large/cover.jpg",
                "media_info": {"mp4_hd_url": "https://f.video.weibocdn.com/hd.mp4"},
            },
        }
    }
    with respx.mock:
        respx.get("https://t.cn/A6xYz").mock(
            return_value=httpx.Response(302, headers={"Location": f"https://weibo.com/1234567890/{status_id}"})
        )
        respx.get(f"https://weibo.com/1234567890/{status_id}").mock(
            return_value=httpx.Response(200, text="<html>redirect target</html>")
        )
        respx.get(f"https://m.weibo.cn/statuses/show?id={status_id}").mock(
            return_value=httpx.Response(200, json=show_data)
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await WeiboParser(client).parse("https://t.cn/A6xYz")

    assert result.platform == Platform.WEIBO
    assert result.type == "mixed"
    assert result.medias[0].kind.value == "video"
    assert result.medias[0].url == "https://f.video.weibocdn.com/hd.mp4"
    images = [m for m in result.medias if m.kind.value == "image"]
    assert len(images) == 2
    assert images[0].url == "https://wx1.sinaimg.cn/mw2000/a_o.jpg"
    assert images[1].url == "https://wx1.sinaimg.cn/mw2000/b_o.jpg"


@pytest.mark.asyncio
async def test_weibo_images_only():
    status_id = "XyZ987"
    show_data = {
        "data": {
            "text": "只有图片",
            "user": {"screen_name": "作者"},
            "pics": [{"url": "https://wx4.sinaimg.cn/large/a.jpg"}],
        }
    }
    with respx.mock:
        respx.get("https://m.weibo.cn/status/XyZ987").mock(
            return_value=httpx.Response(302, headers={"Location": f"https://weibo.com/1234567890/{status_id}"})
        )
        respx.get(f"https://weibo.com/1234567890/{status_id}").mock(
            return_value=httpx.Response(200, text="<html>redirect target</html>")
        )
        respx.get(f"https://m.weibo.cn/statuses/show?id={status_id}").mock(
            return_value=httpx.Response(200, json=show_data)
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await WeiboParser(client).parse("https://m.weibo.cn/status/XyZ987")

    assert result.type == "image"
    assert len(result.medias) == 1


@pytest.mark.asyncio
async def test_weibo_mixed_post_completes_media_beyond_nine():
    """混合发布接口只返回前 9 个媒体，第 10 个起要用 pic_ids 补全（18 图微博只出 9 图的回归）。"""
    status_id = "Rkw7rC72F"
    show_data = {
        "data": {
            "text": "混合发布",
            "user": {"screen_name": "作者"},
            "pic_num": 4,
            "pic_ids": ["pidA", "pidB", "pidC", "pidD"],
            # 接口对混合发布只给前 9 个媒体的种类与顺序（此处缩成 4 个）
            "mix_media_ids": ["pidA", "http://t.cn/AVideo1", "pidB", "http://t.cn/AVideo2"],
            "pics": [
                {"pid": "pidA", "url": "https://wx1.sinaimg.cn/orj360/a.jpg"},
                {
                    "pid": "poster1",
                    "type": "video",
                    "videoSrc": "https://f.video.weibocdn.com/v1.mp4",
                    "large": {"url": "https://wx1.sinaimg.cn/large/poster1.jpg"},
                },
                {
                    "pid": "pidB",
                    "type": "livephoto",
                    "videoSrc": "https://livephoto.weibo.cn/b.mov",
                    "large": {"url": "https://wx2.sinaimg.cn/mw2000/b.jpg"},
                },
                {
                    "pid": "poster2",
                    "type": "video",
                    "videoSrc": "https://f.video.weibocdn.com/v2.mp4",
                    "url": "https://wx3.sinaimg.cn/orj360/poster2.jpg",
                },
            ],
            "page_info": {
                "page_pic": "https://wx1.sinaimg.cn/large/poster1.jpg",
                "media_info": {"stream_url_hd": "https://f.video.weibocdn.com/v1-540p.mp4"},
            },
        }
    }
    with respx.mock:
        respx.get(f"https://m.weibo.cn/statuses/show?id={status_id}").mock(
            return_value=httpx.Response(200, json=show_data)
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            result = await WeiboParser(client).parse(f"https://weibo.com/1234567890/{status_id}")

    assert result.type == "mixed"
    assert [m.kind.value for m in result.medias] == [
        "image", "video", "image", "video", "image", "image",
    ]
    # 前 9 个媒体按 mix 顺序还原，视频取 pics 明细里的 720p 直链，不重复 page_info 的 540p
    assert result.medias[1].url == "https://f.video.weibocdn.com/v1.mp4"
    assert result.medias[1].cover == "https://wx1.sinaimg.cn/large/poster1.jpg"
    assert result.medias[3].url == "https://f.video.weibocdn.com/v2.mp4"
    # 第 10 个媒体起只有 pic_id，按 id 拼原图直链
    assert result.medias[4].url == "https://wx1.sinaimg.cn/large/pidC.jpg"
    assert result.medias[5].url == "https://wx1.sinaimg.cn/large/pidD.jpg"


@pytest.mark.asyncio
async def test_weibo_text_only_fails():
    status_id = "TextOnly"
    with respx.mock:
        respx.get("https://m.weibo.cn/status/TextOnly").mock(
            return_value=httpx.Response(302, headers={"Location": f"https://weibo.com/1234567890/{status_id}"})
        )
        respx.get(f"https://weibo.com/1234567890/{status_id}").mock(
            return_value=httpx.Response(200, text="<html>redirect target</html>")
        )
        respx.get(f"https://m.weibo.cn/statuses/show?id={status_id}").mock(
            return_value=httpx.Response(200, json={"data": {"text": "纯文字微博", "user": {"screen_name": "作者"}}})
        )
        async with httpx.AsyncClient(follow_redirects=True) as client:
            try:
                await WeiboParser(client).parse("https://m.weibo.cn/status/TextOnly")
            except Exception as exc:  # noqa: BLE001
                assert getattr(exc, "code", None) == "parse_failed"
            else:
                raise AssertionError("应当抛出 ParseError")

