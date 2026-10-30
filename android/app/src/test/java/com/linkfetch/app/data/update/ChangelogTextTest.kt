package com.linkfetch.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTextTest {

    private val markdown = """
        # 更新日志

        ## 1.9.0

        ### 新增

        - **分享入口**：可从其他应用直接分享链接。
        - 历史记录增强。

        ### 改进

        - 底栏动画。

        ### 测试

        - 新增契约 fixture。

        ## 1.8.3

        - 黑白灰改版。
    """.trimIndent()

    @Test
    fun `extracts only the requested version`() {
        val section = changelogSection(markdown, "1.9.0")
        assertTrue(section.contains("分享入口"))
        assertTrue(section.contains("底栏动画"))
        assertFalse(section.contains("黑白灰改版"))
    }

    @Test
    fun `missing version returns empty`() {
        assertEquals("", changelogSection(markdown, "9.9.9"))
    }

    @Test
    fun `local display hides test section and markdown markers`() {
        val text = localChangelogForDisplay(markdown, "1.9.0")
        assertTrue(text.contains("分享入口：可从其他应用直接分享链接。"))
        assertFalse(text.contains("测试"))
        assertFalse(text.contains("契约 fixture"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("###"))
    }

    @Test
    fun `release notes stop at installation section`() {
        val raw = """
            黑白灰改版落地。

            - 全盘去色
            - 悬浮 Dock

            安装：下载 APK 覆盖安装即可。
        """.trimIndent()
        assertEquals(
            "黑白灰改版落地。\n\n- 全盘去色\n- 悬浮 Dock",
            cleanReleaseNotes(raw),
        )
    }

    @Test
    fun `release notes drop bare links`() {
        val raw = "更新内容\n\nhttps://github.com/Zzzurt/LinkFetch\n\n- 修复动画"
        assertEquals("更新内容\n\n- 修复动画", cleanReleaseNotes(raw))
    }

    @Test
    fun `release notes keep normal content`() {
        val raw = "本次更新\n\n- 新增分享入口\n- 修复显示问题"
        assertEquals(raw, cleanReleaseNotes(raw))
    }

    @Test
    fun `markdown links become plain text`() {
        assertEquals(
            "- 详情见 仓库",
            cleanReleaseNotes("- 详情见 [仓库](https://github.com/Zzzurt/LinkFetch)"),
        )
    }

    @Test
    fun `update bullet starting with install wording is kept`() {
        val raw = "- 安装体验优化\n- 修复闪退"
        assertEquals(raw, cleanReleaseNotes(raw))
    }
}
