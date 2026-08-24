package com.linkfetch.app.data.parser

/** 本地直连解析错误，code 与后端统一错误码一致。rawBody 携带平台原始响应，用于诊断。 */
class LocalParseException(
    val code: String,
    message: String,
    val rawBody: String? = null,
) : Exception(message)
