package com.wanggaowan.android.dev.tools.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

private val LOG = logger<TranslateUtils>()

/**
 * 语言翻译工具
 *
 * @author Created by wanggaowan on 2024/1/5 08:44
 */
object TranslateUtils {

    private var httpClient: HttpClient? = null
    fun createHttpClient(): HttpClient {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10000))
                .build()
        }
        return httpClient!!
    }

    fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String? {
        val uuid = UUID.randomUUID().toString()
        val dateformat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        dateformat.timeZone = TimeZone.getTimeZone("UTC")
        val time = dateformat.format(Date())

        var accessKeyId = "TFRBSTV0UnFrbzY3QThVeFZDOGt4dHNu"
        accessKeyId = String(mapValue(accessKeyId))
        val queryMap = mutableMapOf<String, String>()
        queryMap["AccessKeyId"] = accessKeyId
        queryMap["Action"] = "TranslateGeneral"
        queryMap["Format"] = "JSON"
        queryMap["FormatType"] = "text"
        queryMap["RegionId"] = "cn-hangzhou"
        queryMap["Scene"] = "general"
        queryMap["SignatureVersion"] = "1.0"
        queryMap["SignatureMethod"] = "HMAC-SHA1"
        queryMap["Status"] = "Available"
        queryMap["SignatureNonce"] = uuid
        queryMap["SourceLanguage"] = sourceLanguage
        queryMap["SourceText"] = text
        queryMap["TargetLanguage"] = targetLanguage
        queryMap["Timestamp"] = time
        queryMap["Version"] = "2018-10-12"
        var queryString = getCanonicalizedQueryString(queryMap, queryMap.keys.toTypedArray())

        val stringToSign = "GET" + "&" + encodeURI("/") + "&" + encodeURI(queryString)
        val signature = encodeURI(Base64.getEncoder().encodeToString(signatureMethod(stringToSign)))
        queryString += "&Signature=$signature"
        try {
            val request: HttpRequest? = HttpRequest.newBuilder()
                .uri(URI.create("https://mt.cn-hangzhou.aliyuncs.com/?$queryString"))
                .GET()
                .build()

            val response: HttpResponse<String?> =
                createHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            val body = response.body() ?: ""
            if (body.isEmpty()) {
                return null
            }

            // {"RequestId":"A721413A-7DCD-51B0-8AEE-FCE433CEACA2","Data":{"WordCount":"4","Translated":"Test Translation"},"Code":"200"}
            val jsonObject = Gson().fromJson(body, JsonObject::class.java)
            val code = jsonObject.getAsJsonPrimitive("Code").asString
            if (code != "200") {
                LOG.error("阿里翻译失败,响应结果：$body，模版语言：${sourceLanguage},目标语言：${targetLanguage}, 翻译失败文本：$text")
                return null
            }

            val data = jsonObject.getAsJsonObject("Data") ?: return null
            return data.getAsJsonPrimitive("Translated").asString
        } catch (e: Exception) {
            LOG.error("阿里翻译失败,异常内容：${e.message}，模版语言：${sourceLanguage},目标语言：${targetLanguage}, 翻译失败文本：$text")
            return null
        }
    }

    @Throws(java.lang.Exception::class)
    private fun signatureMethod(stringToSign: String?): ByteArray? {
        val secret = "V3FWRGI3c210UW9rOGJUOXF2VHhENnYzbmF1bjU1Jg=="
        if (stringToSign == null) {
            return null
        }
        val sha256Hmac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(mapValue(secret), "HmacSHA1")
        sha256Hmac.init(secretKey)
        return sha256Hmac.doFinal(stringToSign.toByteArray())
    }

    @Throws(java.lang.Exception::class)
    private fun getCanonicalizedQueryString(
        query: Map<String, String?>,
        keys: Array<String>
    ): String {
        if (query.isEmpty()) {
            return ""
        }
        if (keys.isEmpty()) {
            return ""
        }

        Arrays.sort(keys)

        var key: String?
        var value: String?
        val sb = StringBuilder()
        for (i in keys.indices) {
            key = keys[i]
            sb.append(encodeURI(key))
            value = query[key]
            sb.append("=")
            if (!value.isNullOrEmpty()) {
                sb.append(encodeURI(value))
            }
            sb.append("&")
        }
        return sb.deleteCharAt(sb.length - 1).toString()
    }

    private fun mapValue(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private fun encodeURI(content: String): String {
        return try {
            URLEncoder.encode(content, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("%7E", "~")
                .replace("*", "%2A")
        } catch (_: UnsupportedEncodingException) {
            content
        }
    }

    fun mapStrToKey(str: String?, isFormat: Boolean): String? {
        var value = fixNewLineFormatError(str)?.replace("\\n", "_")
        if (value.isNullOrEmpty()) {
            return null
        }

        // \pP：中的小写p是property的意思，表示Unicode属性，用于Unicode正则表达式的前缀。
        //
        // P：标点字符
        //
        // L：字母；
        //
        // M：标记符号（一般不会单独出现）；
        //
        // Z：分隔符（比如空格、换行等）；
        //
        // S：符号（比如数学符号、货币符号等）；
        //
        // N：数字（比如阿拉伯数字、罗马数字等）；
        //
        // C：其他字符
        value = value.lowercase().replace(Regex("[\\pP\\pS]"), "_")
            .replace(" ", "_")
        if (isFormat) {
            value += "_format"
        }

        value = value.replace("_____", "_")
            .replace("____", "_")
            .replace("___", "_")
            .replace("__", "_")

        if (value.startsWith("_")) {
            value = value.substring(1, value.length)
        }

        if (value.endsWith("_")) {
            value = value.substring(0, value.length - 1)
        }

        return value
    }

    /// 修复翻译错误，如占位符为大写，\n，%s翻译后被分开成 \ n,% s等错误
    fun fixTranslateError(
        translate: String?,
        targetLanguage: String,
        placeHolderCount: Int? = null
    ): String? {
        var translateStr = fixTranslatePlaceHolderStr(translate, placeHolderCount)
        translateStr = fixNewLineFormatError(translateStr)
        if (targetLanguage == "en") {
            translateStr = fixEnTranslatePlaceHolderStr(translateStr)
        }
        translateStr = translateStr?.replace("'", "\\'")
        return translateStr
    }

    /// 修复因翻译，导致占位符被翻译为大写的问题
    private fun fixTranslatePlaceHolderStr(
        translate: String?,
        placeHolderCount: Int? = null
    ): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText: String = translate
        val placeHolders = listOf("s", "d", "f", "l")
        placeHolders.forEach {
            // 如果templateEntryList有值，说明是kotlin语言，此时取最大占位符数量
            for (i in 0 until max(6, (placeHolderCount ?: 0) + 1)) {
                // 去除翻译后占位符之间的空格
                translateText = if (i == 0) {
                    // 正则：%\s+s
                    val value = fixFormatError(Regex("%\\s+$it"), translateText, "%$it")
                    fixFormatError(Regex("%\\s+${it.uppercase()}"), value, "%$it")
                } else {
                    // 正则：%\s*1\s*\$\s*s
                    val value =
                        fixFormatError(Regex("%\\s*$i\\s*\\$\\s*$it"), translateText, "%$i$$it")
                    fixFormatError(Regex("%\\s*$i\\s*\\$\\s*${it.uppercase()}"), value, "%$i$$it")
                }
            }
        }
        return translateText
    }

    /// 修复英语，翻译后占位符和单词连在一起的问题
    private fun fixEnTranslatePlaceHolderStr(translate: String?): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText: String = translate
        val placeHolders = listOf("s", "d", "f", "l")
        placeHolders.forEach {
            for (i in 0 until 6) {
                // 去除翻译后占位符之间的空格
                val placeHolder = if (i == 0) {
                    "%$it"
                } else {
                    "%$i$$it"
                }
                translateText = insertWhiteSpace(0, translateText, placeHolder)
            }
        }
        return translateText
    }

    // 在占位符和单词直接插入空格
    private fun insertWhiteSpace(start: Int, text: String, placeHolder: String): String {
        var translateText = text
        val index = translateText.indexOf(placeHolder, start)
        if (index > 0) {
            val chart = translateText.substring(index - 1, index)
            if (chart.matches(Regex("[a-zA-Z0-9]"))) {
                translateText =
                    "${translateText.substring(0, index)} ${translateText.substring(index)}"
                translateText =
                    insertWhiteSpace(index + placeHolder.length + 1, translateText, placeHolder)
            }
        }
        return translateText
    }

    // 修复格式错误，如\n,翻译成 \ n
    private fun fixNewLineFormatError(text: String?): String? {
        if (text.isNullOrEmpty()) {
            return text
        }

        val regex = Regex("\\\\\\s+n") // \\\s+n
        return fixFormatError(regex, text, "\\n")
    }

    private tailrec fun fixFormatError(
        regex: Regex,
        text: String,
        placeHolder: String,
        oldRange: IntRange? = null
    ): String {
        if (text.isEmpty()) {
            return text
        }
        val matchResult = regex.find(text) ?: return text
        if (matchResult.range == oldRange) {
            return text
        }

        return fixFormatError(
            regex,
            text.replaceRange(matchResult.range, placeHolder),
            placeHolder,
            matchResult.range
        )
    }
}
