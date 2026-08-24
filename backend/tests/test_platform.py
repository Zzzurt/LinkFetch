from app.models import ParseError, Platform
from app.platform import detect_platform


def test_detect_xhs_short_link():
    assert detect_platform("https://xhslink.com/a/bcDEF") == Platform.XHS


def test_detect_xhs_web():
    assert detect_platform("https://www.xiaohongshu.com/explore/64abc123") == Platform.XHS


def test_detect_douyin_short_link():
    assert detect_platform("https://v.douyin.com/iAbCdEf/") == Platform.DOUYIN


def test_detect_douyin_web():
    assert detect_platform("https://www.douyin.com/video/7123456789012345678") == Platform.DOUYIN


def test_detect_weibo_tcn():
    assert detect_platform("https://t.cn/A6xYz") == Platform.WEIBO


def test_detect_weibo_mobile():
    assert detect_platform("https://m.weibo.cn/status/1234567890") == Platform.WEIBO


def test_detect_x_status():
    assert detect_platform("https://x.com/i/status/2083053411524850111") == Platform.X

def test_detect_x_twitter_and_tco():
    assert detect_platform("https://twitter.com/user/status/1") == Platform.X
    assert detect_platform("https://t.co/abc123") == Platform.X

def test_unsupported_link():
    try:
        detect_platform("https://www.bilibili.com/video/BV1xx")
    except ParseError as exc:
        assert exc.code == "unsupported_link"
        assert exc.http_status == 400
    else:
        raise AssertionError("应当抛出 ParseError")


def test_empty_url():
    try:
        detect_platform("not a url")
    except ParseError as exc:
        assert exc.code == "unsupported_link"
    else:
        raise AssertionError("应当抛出 ParseError")

