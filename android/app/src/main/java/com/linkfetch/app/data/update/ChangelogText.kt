package com.linkfetch.app.data.update

/**
 * 本地 CHANGELOG.md 中指定版本的小节。
 *
 * 约定文件结构为 `## 1.9.0` + 若干 `### 分类`；从目标标题之后收集到下一个
 * 二级标题为止。找不到目标版本时返回空串。
 */
internal fun changelogSection(markdown: String, version: String): String {
    val target = version.trim().removePrefix("v")
    val body = mutableListOf<String>()
    var collecting = false
    for (line in markdown.replace("\r\n", "\n").lines()) {
        val heading = VERSION_HEADING.matchEntire(line.trim())
        if (heading != null) {
            if (collecting) break
            collecting = heading.groupValues[1].removePrefix("v") == target
        } else if (collecting) {
            body.add(line)
        }
    }
    return body.joinToString("\n").trim()
}

/** 本地日志的展示文本：当前版本小节 + 隐藏「测试」节 + 去掉 Markdown 记号。 */
internal fun localChangelogForDisplay(markdown: String, version: String): String =
    markdownToPlainText(stripTestSection(changelogSection(markdown, version)))

/**
 * GitHub Release 正文的展示文本。
 *
 * 发布正文里常带「安装：…」「仓库：…」这类不属于更新内容的段落，从第一条
 * 此类段落起整段丢弃；纯链接行也一并去掉。
 */
internal fun cleanReleaseNotes(raw: String): String {
    val kept = mutableListOf<String>()
    for (line in raw.replace("\r\n", "\n").lines()) {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            if (isNonUpdateSection(trimmed)) break
            if (isLinkOnlyLine(trimmed)) continue
        }
        kept.add(line)
    }
    return markdownToPlainText(stripTestSection(kept.joinToString("\n")))
}

private val VERSION_HEADING = Regex("""^##\s+(\S+)\s*$""")
private val MD_LINK = Regex("""\[([^\]]+)]\(https?://[^)]+\)""")
private val MD_HEADING = Regex("""^#{1,6}\s*""", RegexOption.MULTILINE)
private val BARE_URL = Regex("""^<?https?://\S+>?$""")
private val MD_LINK_ONLY = Regex("""^\[[^\]]*]\(https?://[^)]+\)$""")

private val NON_UPDATE_SECTION = Regex(
    """^(安装|安装说明|安装步骤|下载|下载地址|仓库|项目地址|源码|Release|Releases|GitHub)(\s*[:：].*)?$""",
    RegexOption.IGNORE_CASE,
)

private fun isNonUpdateSection(line: String): Boolean {
    // 允许标题、列表符号或引用符号在前，例如「### 安装」「- 安装：…」；
    // 只匹配整行就是该段落或「安装：…」这种冒号开头，避免误伤「安装体验优化」这类更新项。
    val normalized = line.trimStart('#', '-', '*', '>', ' ').trimStart()
    return NON_UPDATE_SECTION.matches(normalized)
}

private fun isLinkOnlyLine(line: String): Boolean =
    BARE_URL.matches(line) || MD_LINK_ONLY.matches(line)

/** 去掉「### 测试」直到下一个同级/更高级标题之间的内容。 */
private fun stripTestSection(body: String): String {
    val kept = mutableListOf<String>()
    var hiding = false
    body.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("### ")) {
            hiding = trimmed.removePrefix("### ").trim().startsWith("测试")
        } else if (trimmed.startsWith("## ")) {
            hiding = false
        }
        if (!hiding) kept.add(line)
    }
    return kept.joinToString("\n").trim()
}

private fun markdownToPlainText(text: String): String = text
    .replace(MD_LINK, "$1")
    .replace(MD_HEADING, "")
    .replace("**", "")
    .replace("`", "")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()
