package com.linkfetch.app.data

import com.linkfetch.app.data.model.ParseResponseDto

/**
 * 当前解析结果的内存态（单 Activity 内跨页面传递）。
 */
object ParseResultStore {
    var originalUrl: String = ""
    var result: ParseResponseDto? = null
}

