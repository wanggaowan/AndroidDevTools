package com.wanggaowan.android.dev.tools.actions

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.LocalTimeCounter
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.wanggaowan.android.dev.tools.settings.PluginSettings
import com.wanggaowan.android.dev.tools.ui.UIColor
import com.wanggaowan.android.dev.tools.utils.NotificationUtils
import com.wanggaowan.android.dev.tools.utils.Toast
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject
import com.wanggaowan.android.dev.tools.utils.ex.resRootDir
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max

private val LOG = logger<ExtractStr2L10n>()

/**
 * 提取文本为多语言
 *
 * @author Created by wanggaowan on 2023/10/7 10:56
 */
class ExtractStr2L10n : DumbAwareAction() {

    private var selectedPsiElement: PsiElement? = null
    private var selectedPsiFile: PsiFile? = null
    private var fileType: Int = 0

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        if (!project.isAndroidProject) {
            e.presentation.isVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isVisible = false
            return
        }

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (psiFile == null || psiFile.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (!psiFile.name.endsWith(".xml") && !psiFile.name.endsWith(".java") && !psiFile.name.endsWith(".kt")) {
            e.presentation.isVisible = false
            return
        }

        val psiElement: PsiElement? = psiFile.findElementAt(editor.selectionModel.selectionStart)
        if (psiElement == null) {
            e.presentation.isVisible = false
            return
        }

        if (psiFile.name.endsWith(".xml")) {
            val parent = psiElement.getParentOfType<XmlAttributeValue>(true)
            if (parent == null) {
                e.presentation.isVisible = false
                return
            }

            fileType = 0
            selectedPsiFile = psiFile
            selectedPsiElement = parent
            e.presentation.isVisible = true
            return
        }

        if (psiFile.name.endsWith(".java")) {
            val parent = psiElement.getParentOfType<PsiLiteralExpression>(true)
            if (parent == null) {
                e.presentation.isVisible = false
                return
            }

            fileType = 1
            selectedPsiFile = psiFile
            selectedPsiElement = parent
            e.presentation.isVisible = true
            return
        }

        if (psiFile.name.endsWith(".kt")) {
            val parent = psiElement.getParentOfType<KtStringTemplateExpression>(true)
            if (parent == null) {
                e.presentation.isVisible = false
                return
            }

            fileType = 2
            selectedPsiFile = psiFile
            selectedPsiElement = parent
            e.presentation.isVisible = true
            return
        }


        e.presentation.isVisible = false
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedFile = selectedPsiFile ?: return
        val selectedElement = selectedPsiElement ?: return
        val resRootDir = project.resRootDir
        if (resRootDir == null) {
            NotificationUtils.showBalloonMsg(
                project,
                "未配置strings.xml模板文件，请提供res/values/strings.xml模版文件",
                NotificationType.ERROR
            )
            return
        }

        val stringsFile = VirtualFileManager.getInstance().findFileByUrl("file://${resRootDir.path}/values/strings.xml")
        if (stringsFile == null || stringsFile.isDirectory) {
            NotificationUtils.showBalloonMsg(
                project,
                "未配置strings.xml模板文件，请提供res/values/strings.xml模版文件",
                NotificationType.ERROR
            )
            return
        }

        val stringsPsiFile = stringsFile.toPsiFile(project) ?: return
        val xmlTag = stringsPsiFile.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()
        // 得到的结果格式：'xx', "xx", 'xx$a', "xx$a", 'xx${a??''}', "xx${a??''}"
        var text = selectedElement.text
        if (text.length > 2) {
            // 去除前后单引号或双引号
            text = text.substring(1, text.length - 1)
        }

        var translateText = text.trim()
        val templateEntryList = mutableListOf<KtStringTemplateEntry>()
        if (fileType == 2) {
            findAllKTStringTemplateEntry(selectedElement.firstChild, templateEntryList)
        }

        var isFormat = false
        if (templateEntryList.isNotEmpty()) {
            isFormat = true
            templateEntryList.indices.forEach {
                val element = templateEntryList[it].text
                var index = text.indexOf(element)
                if (index != -1) {
                    text = text.replaceRange(index, index + element.length, "%${it + 1}\$s")
                }

                index = translateText.indexOf(element)
                if (index != -1) {
                    translateText = translateText.replaceRange(index, index + element.length, "")
                }
            }
        }

        val oldLength = translateText.length
        translateText = removeStrPlaceHolder(translateText, templateEntryList)!!
        if (!isFormat) {
            isFormat = translateText.length != oldLength
        }
        translateText = translateText.trim()


        val existKey: String? = getExistKeyByValue(xmlTag, text)
        val otherStringsFile = mutableListOf<TranslateStringsFile>()
        if (PluginSettings.getExtractStr2L10nTranslateOther(project)) {
            stringsPsiFile.virtualFile?.parent?.parent?.children?.let { files ->
                for (file in files) {
                    val name = file.name
                    if (!name.startsWith("values-")) {
                        continue
                    }

                    val stringsXml = file.findChild("strings.xml")
                    val stringsPsiXml = stringsXml?.toPsiFile(project) ?: continue
                    val xmlTag2 = stringsPsiXml.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()
                        ?: continue
                    if (existKey != null && isExistKey(xmlTag2, existKey)) {
                        // 其它语言已存在当前key
                        continue
                    }

                    val targetLanguage = name.substring("values-".length)
                    if (targetLanguage.isNotEmpty()) {
                        otherStringsFile.add(TranslateStringsFile(targetLanguage, stringsPsiXml, xmlTag2))
                    }
                }
            }
        }

        changeData(
            project,
            selectedFile,
            selectedElement,
            existKey,
            templateEntryList,
            text,
            translateText,
            xmlTag,
            stringsPsiFile,
            otherStringsFile,
            isFormat
        )
    }

    /// 移除字符串中的占位符
    private fun removeStrPlaceHolder(
        translate: String?,
        templateEntryList: List<KtStringTemplateEntry>,
    ): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText = translate
        val placeHolders = listOf("s", "d", "f", "l")
        placeHolders.forEach {
            // 如果templateEntryList有值，说明是kotlin语言，此时取最大占位符数量
            for (i in 0 until max(6, templateEntryList.size + 1)) {
                // 去除翻译后占位符之间的空格
                translateText = if (i == 0) {
                    translateText?.replace("%$it", "")
                } else {
                    translateText?.replace("%$i\$$it", "")
                }
            }
        }
        return translateText
    }

    private fun changeData(
        project: Project,
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        existKey: String?,
        templateEntryList: List<KtStringTemplateEntry>,
        originalText: String,
        translateText: String,
        xmlTag: XmlTag?,
        stringsPsiFile: PsiFile,
        otherStringsFile: List<TranslateStringsFile>,
        isFormat: Boolean
    ) {

        if (existKey != null) {
            WriteCommandAction.runWriteCommandAction(project) {
                replaceElement(selectedFile, selectedElement, templateEntryList, existKey)
            }
        } else {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translate") {
                override fun run(progressIndicator: ProgressIndicator) {
                    progressIndicator.isIndeterminate = true
                    var finish = false
                    CoroutineScope(Dispatchers.Default).launch launch2@{
                        val enTranslate = translate(translateText, "en")
                        val key = mapStrToKey(enTranslate, isFormat)
                        otherStringsFile.forEach { file ->
                            if (file.targetLanguage == "en" && !isFormat) {
                                file.translate = enTranslate
                            } else {
                                file.translate = translate(originalText, file.targetLanguage)
                                if (isFormat) {
                                    file.translate = fixTranslatePlaceHolderStr(file.translate, templateEntryList)
                                    if (file.targetLanguage == "en") {
                                        file.translate = fixEnTranslatePlaceHolderStr(file.translate)
                                    }
                                }
                            }
                        }

                        CoroutineScope(Dispatchers.Main).launch {
                            var showRename = false
                            if (key == null || PluginSettings.getExtractStr2L10nShowRenameDialog(project)) {
                                showRename = true
                            } else {
                                if (xmlTag?.findFirstSubTag(key) != null) {
                                    showRename = true
                                }
                            }

                            finish = true
                            progressIndicator.isIndeterminate = false
                            progressIndicator.fraction = 1.0
                            if (showRename) {
                                val newKey = renameKey(project, key, xmlTag, otherStringsFile)
                                    ?: return@launch
                                WriteCommandAction.runWriteCommandAction(project) {
                                    insertElement(project, stringsPsiFile, xmlTag, newKey, originalText)
                                    replaceElement(selectedFile, selectedElement, templateEntryList, newKey)
                                    otherStringsFile.forEach { file ->
                                        val tl = file.translate
                                        if (!tl.isNullOrEmpty()) {
                                            insertElement(
                                                project,
                                                file.stringsFile,
                                                file.xmlTag,
                                                newKey,
                                                tl,
                                            )
                                        }
                                    }
                                }
                            } else {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    insertElement(project, stringsPsiFile, xmlTag, key!!, originalText)
                                    replaceElement(selectedFile, selectedElement, templateEntryList, key)
                                    otherStringsFile.forEach { file ->
                                        val tl = file.translate
                                        if (!tl.isNullOrEmpty()) {
                                            insertElement(
                                                project,
                                                file.stringsFile,
                                                file.xmlTag,
                                                key,
                                                tl,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    while (!finish) {
                        Thread.sleep(100)
                    }
                    progressIndicator.isIndeterminate = false
                    progressIndicator.fraction = 1.0
                }
            })
        }
    }

    /// 修复因翻译，导致占位符被翻译为大写的问题
    private fun fixTranslatePlaceHolderStr(translate: String?, templateEntryList: List<KtStringTemplateEntry>): String? {
        if (translate.isNullOrEmpty()) {
            return null
        }

        var translateText: String = translate
        val placeHolders = listOf("s", "d", "f", "l")
        placeHolders.forEach {
            // 如果templateEntryList有值，说明是kotlin语言，此时取最大占位符数量
            for (i in 0 until max(6, templateEntryList.size + 1)) {
                // 去除翻译后占位符之间的空格
                translateText = if (i == 0) {
                    translateText.replace("% $it", "%$it")
                } else {
                    translateText.replace("% $i \$ $it", "%$i\$$it")
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
                    "%$i\$$it"
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
                translateText = "${translateText.substring(0, index)} ${translateText.substring(index)}"
                translateText = insertWhiteSpace(index + placeHolder.length + 1, translateText, placeHolder)
            }
        }
        return translateText
    }

    private tailrec fun findAllKTStringTemplateEntry(
        psiElement: PsiElement?,
        list: MutableList<KtStringTemplateEntry>
    ) {
        if (psiElement == null) {
            return
        }

        if (psiElement is KtSimpleNameStringTemplateEntry) {
            list.add(psiElement)
        } else if (psiElement is KtBlockStringTemplateEntry) {
            list.add(psiElement)
        }

        findAllKTStringTemplateEntry(psiElement.nextSibling, list)
    }

    // 重命名多语言在strings.xml文件中的key
    private fun renameKey(
        project: Project,
        translate: String?,
        xmlTag: XmlTag?,
        otherStringsFile: List<TranslateStringsFile>
    ): String? {
        val dialog = InputKeyDialog(project, translate, xmlTag, otherStringsFile)
        dialog.show()
        if (dialog.exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null
        }

        return dialog.getValue()
    }

    private fun replaceElement(
        selectedFile: PsiFile,
        selectedElement: PsiElement,
        templateEntryList: List<KtStringTemplateEntry>,
        key: String
    ) {

        val content = if (templateEntryList.isEmpty() || fileType == 0) {
            if (fileType == 0) {
                "\"@string/$key\""
            } else {
                "getString(R.string.$key)"
            }
        } else {
            val builder = StringBuilder("String.format(getString(R.string.$key), ")
            var index = 0
            templateEntryList.forEach {
                if (index > 0) {
                    builder.append(", ")
                }
                val text = it.text
                if (it is KtSimpleNameStringTemplateEntry) {
                    builder.append(text.substring(1))
                } else {
                    builder.append(it.text.substring(2, text.length - 1))
                }
                index++
            }
            builder.append(")")
            builder.toString()
        }

        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(selectedFile.virtualFile)
        document?.replaceString(selectedElement.startOffset, selectedElement.endOffset, content)
    }

    private fun insertElement(
        project: Project,
        stringsPsiFile: PsiFile,
        xmlTag: XmlTag?,
        key: String,
        value: String,
    ) {

        if (xmlTag != null && xmlTag.subTags.isNotEmpty()) {
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.${XmlFileType.INSTANCE.defaultExtension}",
                XmlFileType.INSTANCE,
                "<string name=\"$key\">$value</string>",
                LocalTimeCounter.currentTime(),
                false
            )
            val temp = psiFile.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()
            if (temp != null) {
                xmlTag.addSubTag(temp, false)
            }
        } else {
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.${XmlFileType.INSTANCE.defaultExtension}",
                XmlFileType.INSTANCE,
                "<resources>\n" +
                    "    <string name=\"$key\">$value</string>\n" +
                    "</resources>",
                LocalTimeCounter.currentTime(),
                false
            )
            stringsPsiFile.add(psiFile.firstChild)
        }

        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(stringsPsiFile.virtualFile)
        if (document != null) {
            manager.saveDocument(document)
        } else {
            manager.saveAllDocuments()
        }
    }

    private suspend fun translate(
        text: String,
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
        queryMap["SourceLanguage"] = "zh"
        queryMap["SourceText"] = text
        queryMap["TargetLanguage"] = targetLanguage
        queryMap["Timestamp"] = time
        queryMap["Version"] = "2018-10-12"
        var queryString = getCanonicalizedQueryString(queryMap, queryMap.keys.toTypedArray())

        val stringToSign = "GET" + "&" + encodeURI("/") + "&" + encodeURI(queryString)
        val signature = encodeURI(Base64.getEncoder().encodeToString(signatureMethod(stringToSign)))
        queryString += "&Signature=$signature"
        try {
            val response = HttpClient(CIO) {
                engine {
                    requestTimeout = 5000
                    endpoint {
                        connectTimeout = 5000
                    }
                }
            }.get("https://mt.cn-hangzhou.aliyuncs.com/?$queryString")
            val body = response.bodyAsText()
            if (body.isEmpty()) {
                return null
            }

            // {"RequestId":"A721413A-7DCD-51B0-8AEE-FCE433CEACA2","Data":{"WordCount":"4","Translated":"Test Translation"},"Code":"200"}
            val jsonObject = Gson().fromJson(body, JsonObject::class.java)
            val code = jsonObject.getAsJsonPrimitive("Code").asString
            if (code != "200") {
                LOG.error("阿里翻译失败：$body")
                return null
            }

            val data = jsonObject.getAsJsonObject("Data") ?: return null
            return data.getAsJsonPrimitive("Translated").asString
        } catch (e: Exception) {
            LOG.error("阿里翻译失败：${e.message}")
            return null
        }
    }

    private fun mapStrToKey(str: String?, isFormat: Boolean): String? {
        if (str.isNullOrEmpty()) {
            return null
        }

        // \pP：中的小写p是property的意思，表示Unicode属性，用于Unicode正表达式的前缀。
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
        var value = str
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
    fun getCanonicalizedQueryString(
        query: Map<String, String?>,
        keys: Array<String>
    ): String {
        if (query.isEmpty()) {
            return ""
        }
        if (keys.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        Arrays.sort(keys)


        var key: String?
        var value: String?
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
            URLEncoder.encode(content, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("%7E", "~")
        } catch (var2: UnsupportedEncodingException) {
            content
        }
    }
}

private fun getExistKeyByValue(xmlTag: XmlTag?, text: String): String? {
    val subTags = xmlTag?.subTags
    if (subTags.isNullOrEmpty()) {
        return null
    }

    for (tag in subTags) {
        if (tag.value.text == text) {
            return tag.getAttributeValue("name")
        }
    }
    return null
}

private fun isExistKey(xmlTag: XmlTag?, key: String): Boolean {
    val subTags = xmlTag?.subTags
    if (subTags.isNullOrEmpty()) {
        return false
    }

    for (tag in subTags) {
        if (tag.getAttributeValue("name") == key) {
            return true
        }
    }
    return false
}

class InputKeyDialog(
    val project: Project,
    private var defaultValue: String?,
    private val xmlTag: XmlTag?,
    private val otherStringsFile: List<TranslateStringsFile>,
) : DialogWrapper(project, false) {

    private val rootPanel: JComponent
    private var contentTextField: JBTextArea? = null
    private var existKey: Boolean = false

    init {
        rootPanel = createRootPanel()
        init()
    }

    override fun createCenterPanel(): JComponent = rootPanel

    override fun getPreferredFocusedComponent(): JComponent? {
        return contentTextField
    }

    private fun createRootPanel(): JComponent {
        val builder = FormBuilder.createFormBuilder()
        builder.addComponent(JLabel("输入多语言key："))

        val existKeyHint = JLabel("已存在相同key")
        existKeyHint.foreground = JBColor.RED
        existKeyHint.font = UIUtil.getFont(UIUtil.FontSize.SMALL, existKeyHint.font)
        existKey = if (defaultValue.isNullOrEmpty()) false else isExistKey(xmlTag, defaultValue!!)
        existKeyHint.isVisible = existKey

        val content = JBTextArea()
        content.text = defaultValue
        content.minimumSize = Dimension(300, 40)
        content.lineWrap = true
        content.wrapStyleWord = true
        contentTextField = content

        val jsp = JBScrollPane(content)
        jsp.minimumSize = Dimension(300, 40)
        jsp.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        )

        content.addFocusListener(object : FocusListener {
            override fun focusGained(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
                if (this@InputKeyDialog.defaultValue == null) {
                    contentTextField?.also {
                        Toast.show(it, MessageType.WARNING, "翻译失败，请输入多语言key")
                    }
                    this@InputKeyDialog.defaultValue = ""
                }
            }

            override fun focusLost(p0: FocusEvent?) {
                jsp.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
                )
            }
        })

        content.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(xmlTag, str)
                existKeyHint.isVisible = existKey
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(xmlTag, str)
                existKeyHint.isVisible = existKey
            }

            override fun changedUpdate(p0: DocumentEvent?) {
                val str = content.text.trim()
                existKey = isExistKey(xmlTag, str)
                existKeyHint.isVisible = existKey
            }
        })

        builder.addComponent(jsp)
        builder.addComponent(existKeyHint)

        if (otherStringsFile.isNotEmpty()) {
            val label = JLabel("以下为其它语言翻译内容：")
            label.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            builder.addComponent(label)

            otherStringsFile.forEach {
                val box = Box.createHorizontalBox()
                box.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)

                val label2 = JLabel("${it.targetLanguage}：")
                label2.preferredSize = Dimension(40, 60)
                box.add(label2)

                val textArea = JBTextArea(it.translate)
                textArea.minimumSize = Dimension(260, 60)
                textArea.lineWrap = true
                textArea.wrapStyleWord = true

                val jsp2 = JBScrollPane(textArea)
                jsp2.minimumSize = Dimension(260, 60)
                box.add(jsp2)

                textArea.addFocusListener(object : FocusListener {
                    override fun focusGained(p0: FocusEvent?) {
                        jsp2.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UIColor.INPUT_FOCUS_COLOR, 2, true),
                            BorderFactory.createEmptyBorder(2, 2, 2, 2)
                        )
                    }

                    override fun focusLost(p0: FocusEvent?) {
                        jsp2.border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(UIColor.INPUT_UN_FOCUS_COLOR, 1, true),
                            BorderFactory.createEmptyBorder(2, 2, 2, 2)
                        )
                    }
                })

                textArea.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }

                    override fun removeUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }

                    override fun changedUpdate(p0: DocumentEvent?) {
                        val str = textArea.text.trim()
                        it.translate = str
                    }
                })

                builder.addComponent(box)
            }
        }

        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    fun getValue(): String {
        return contentTextField?.text ?: ""
    }

    override fun doOKAction() {
        val value = getValue()
        if (value.isEmpty()) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "请输入多语言key")
            }
            return
        }

        if (existKey) {
            contentTextField?.also {
                Toast.show(it, MessageType.WARNING, "已存在相同的key")
            }
            return
        }

        super.doOKAction()
    }
}

// 需要翻译的strings.xml数据
data class TranslateStringsFile(
    val targetLanguage: String,
    val stringsFile: PsiFile,
    val xmlTag: XmlTag?,
    var translate: String? = null
)

