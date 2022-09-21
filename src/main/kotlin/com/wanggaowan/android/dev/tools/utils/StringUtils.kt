package com.wanggaowan.android.dev.tools.utils

import java.util.*

/**
 * 字符工具
 *
 * @author Created by wanggaowan on 2022/5/8 17:31
 */
object StringUtils {
    /**
     * 转成驼峰
     */
    fun toHumpFormat(str: String): String {
        if (str.isEmpty()) {
            return str
        }

        val text = str.replace("^_+".toRegex(), "")
        if (text.isEmpty()) {
            return text
        }

        val strings = text.split("_")
        val stringBuilder = StringBuilder()
        for (element in strings) {
            stringBuilder.append(capitalName(element))
        }
        return stringBuilder.toString()
    }

    /**
     * 将首字母转化为大写
     */
    @JvmStatic
    fun capitalName(text: String): String {
        if (text.isNotEmpty()) {
            return text.substring(0, 1).uppercase(Locale.getDefault()) + text.substring(1)
        }

        return text
    }
}
