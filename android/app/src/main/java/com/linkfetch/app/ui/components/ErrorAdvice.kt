package com.linkfetch.app.ui.components

/**
 * 解析失败时给出「下一步做什么」。
 *
 * 加这一层的起因：失败恰恰是用户最需要知道下一步的时刻，而此前界面只给一句现象描述。
 *
 * **前提（决定了这里只写两条）**：现有错误文案 —— 各 Parser 抛出的 message ——
 * 本身已经包含一部分行动指引：
 * - 小红书 / 微博 / 抖音的 `rate_limited` 文案里已提示「可在设置中配置 Cookie」；
 * - X 的 `network_error` 已明确要求海外网络；
 * - `unsupported_link` 的文案已说明支持的平台范围。
 *
 * 所以这里只补 message **没说到**的动作；一旦某条 message 自己已经说全，就返回 null，
 * 绝不把同一句话换个说法再说一遍（那只会让错误卡变长、不变得更有用）。
 *
 * 错误码与后端统一（见 LocalParseException / ApiException），共四个。
 */
fun errorAdvice(code: String?): String? = when (code) {
    // 现有文案只说「请稍后重试」，没说这次失败意味着什么。
    // 真正缺的信息是：它是一次性限制、不是作品失效，用户不必去改设置或重装。
    "rate_limited" ->
        "这是平台的临时访问限制，与作品本身无关；过几分钟再试通常即可恢复。"

    // parse_failed 的文案都是对现象的描述（页面结构变化 / 未找到数据 / 不含媒体），没有下一步。
    "parse_failed" ->
        "平台接口可能已改版：可先尝试更新 App；仍失败时在设置中切换为「自建服务器」模式。"

    // unsupported_link 与 network_error 的文案已含行动指引，无需补充。
    else -> null
}
