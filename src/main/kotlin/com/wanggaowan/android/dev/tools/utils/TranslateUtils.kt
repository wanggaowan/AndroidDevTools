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
            value = value.dropLast(1)
        }

        return value
    }

    /// 修复翻译错误，如占位符为大写，\n，%s翻译后被分开成 \ n,% s等错误
    fun fixTranslateError(
        translate: String?,
        targetLanguage: String,
    ): String? {
        var translateStr = fixTranslatePlaceHolderStr(translate)
        translateStr = fixNewLineFormatError(translateStr)
        if (targetLanguage != "zh" && targetLanguage != "ja") {
            translateStr = fixEnTranslatePlaceHolderStr(translateStr)
        }
        translateStr = translateStr?.replace("'", "\\'")
        return translateStr
    }

    /// 修复因翻译，导致占位符格式错误，比如%s翻译后是%S，或者中间有空格如% s
    private fun fixTranslatePlaceHolderStr(translate: String?): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText: String = translate
        // 此处只处理常用占位符
        val placeHolders = listOf("s", "d", "f")
        placeHolders.forEach {
            val regex = Regex(getPlaceHolderRegex(it))
            translateText = fixFormatError(regex, translateText)
        }
        return translateText
    }

    /// 修复英语，翻译后占位符和单词连在一起的问题
    private fun fixEnTranslatePlaceHolderStr(translate: String?): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText: String = translate
        val placeHolders = listOf("s", "d", "f")
        placeHolders.forEach {
            val regex = Regex(getPlaceHolderRegex(it))
            translateText = insertWhiteSpace(translateText, regex)
        }
        return translateText
    }

    // 在占位符和单词直间插入空格
    private tailrec fun insertWhiteSpace(text: String, regex: Regex, offset: Int = 0): String {
        if (text.isEmpty()) {
            return text
        }

        val matchResult = regex.find(text, offset) ?: return text
        val start = matchResult.range.first
        val end = matchResult.range.last
        var translateText = text
        var chart = translateText.substring(start - 1, start)
        var offset = 0
        val numberRegex = Regex("[a-zA-Z0-9]")
        if (chart.matches(numberRegex)) {
            translateText =
                "${translateText.take(start)} ${translateText.substring(start)}"
            offset++
        }

        val totalLength = translateText.length
        if (end + offset >= totalLength) {
            return translateText
        }

        chart = translateText.substring(end + offset + 1, end + offset + 2)
        if (chart.matches(numberRegex)) {
            translateText =
                "${translateText.take(end + offset + 1)} ${translateText.substring(end + offset + 1)}"
            offset++
        }

        return insertWhiteSpace(translateText, regex, end + offset)
    }

    // 修复格式错误，如\n,翻译成 \ n
    private fun fixNewLineFormatError(text: String?): String? {
        if (text.isNullOrEmpty()) {
            return text
        }

        var regex = Regex("\\\\\\s+n") // \\\s+n
        val text2 = fixFormatError(regex, text)
        regex = Regex("\\s*\\\\n\\s*")
        return text2.replace(regex, "\\\\n")
    }

    /**
     * 修复格式错误，比如%s翻译后是%S，\n翻译后是\N，或者中间有空格如% s，\ n等
     *
     * [text]为需要修复的文本
     * [regex]为查找错误格式文本的正则表达式
     */
    private tailrec fun fixFormatError(
        regex: Regex,
        text: String,
        offset: Int? = null
    ): String {
        if (text.isEmpty()) {
            return text
        }

        val matchResult = regex.find(text, offset ?: 0) ?: return text
        var placeHolder = text.substring(matchResult.range)
        val oldLength = placeHolder.length
        placeHolder = placeHolder.replace(" ", "").lowercase()

        return fixFormatError(
            regex,
            text.replaceRange(matchResult.range, placeHolder),
            matchResult.range.last - (oldLength - placeHolder.length)
        )
    }

    /**
     * 获取字符串占位符正则表达式
     *
     * [suffix]为占位符后缀，如s、d、f
     */
    fun getPlaceHolderRegex(suffix: String): String {
        // %[-+.#(0-9\s\$]*(s|S)，此正则仅匹配一些常用的规则，并未覆盖所有情况
        // java字符串格式化详细文档可查看java.util.Formatter
        return "%[-+.#(0-9\\s\\\\$]*($suffix|${suffix.uppercase()})"
    }
}
