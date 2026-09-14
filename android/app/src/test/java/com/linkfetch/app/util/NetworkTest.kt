package com.linkfetch.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTest {

    @Test
    fun acceptsLoopbackAndPrivateRanges() {
        // 回环与常见内网地址应被判定为可信（允许 http）
        listOf(
            "localhost",
            "127.0.0.1",
            "10.0.2.2", // 模拟器宿主机
            "10.1.2.3",
            "192.168.1.8",
            "172.16.0.1",
            "172.31.255.254",
            "169.254.10.10",
        ).forEach { host ->
            assertTrue("应判定为私网/回环：$host", isPrivateOrLoopbackHost(host))
        }
    }

    @Test
    fun rejectsPublicHosts() {
        listOf(
            "example.com",
            "8.8.8.8",
            "1.1.1.1",
            "173.194.0.1", // 172.32 已不在 172.16/12 内
            "172.15.0.1",
            "172.32.0.1",
            "11.0.0.1",
            "192.169.0.1",
        ).forEach { host ->
            assertFalse("不应判定为私网：$host", isPrivateOrLoopbackHost(host))
        }
    }

    @Test
    fun rejectsMalformedInput() {
        listOf("", "not-an-ip", "1.2.3", "1.2.3.4.5", "1.2.3.999", "10.0.0.-1").forEach { host ->
            assertFalse("非法输入应返回 false：$host", isPrivateOrLoopbackHost(host))
        }
    }

    @Test
    fun handlesIpv6LoopbackAndBrackets() {
        assertTrue(isPrivateOrLoopbackHost("::1"))
        assertTrue(isPrivateOrLoopbackHost("[::1]"))
    }
}
