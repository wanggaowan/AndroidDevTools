package com.wanggaowan.android.dev.tools.actions.translate

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.wanggaowan.android.dev.tools.settings.PluginSettings
import com.wanggaowan.android.dev.tools.ui.UIColor
import com.wanggaowan.android.dev.tools.utils.NotificationUtils
import com.wanggaowan.android.dev.tools.utils.ProgressUtils
import com.wanggaowan.android.dev.tools.utils.Toast
import com.wanggaowan.android.dev.tools.utils.TranslateUtils
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject
import com.wanggaowan.android.dev.tools.utils.ex.resRootDir
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
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max

/**
 * 提取文本为多语言
 *
 * @author Created by wanggaowan on 2023/10/7 10:56
 */
class ExtractStr2L10nAction : DumbAwareAction() {

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

        if (!psiFile.name.endsWith(".xml")
            && !psiFile.name.endsWith(".java")
            && !psiFile.name.endsWith(".kt")) {
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


            if (psiFile.parent?.name?.startsWith("values") == true) {
                if (psiFile.parent?.parent?.name == "res") {
                    e.presentation.isVisible = false
                    return
                }
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

        val stringsFile = VirtualFileManager.getInstance()
            .findFileByUrl("file://${resRootDir.path}/values/strings.xml")
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

        val otherStringsFile = mutableListOf<TranslateStringsFile>()
        val existKey: String? = getExistKeyByValue(xmlTag, text)
        if (PluginSettings.getExtractStr2L10nTranslateOther(project)) {
            stringsPsiFile.virtualFile?.parent?.parent?.children?.let { files ->
                for (file in files) {
                    val name = file.name
                    if (!name.startsWith("values-")) {
                        continue
                    }

                    val stringsXml = file.findChild("strings.xml")
                    val stringsPsiXml = stringsXml?.toPsiFile(project) ?: continue
                    val xmlTag2 =
                        stringsPsiXml.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()
                            ?: continue
                    if (existKey != null && isExistKey(xmlTag2, existKey)) {
                        // 其它语言已存在当前key
                        continue
                    }

                    val targetLanguage = name.substring("values-".length)
                    if (targetLanguage.isNotEmpty()) {
                        otherStringsFile.add(
                            TranslateStringsFile(
                                targetLanguage,
                                stringsPsiXml,
                                xmlTag2
                            )
                        )
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
                    translateText?.replace("%$i$$it", "")
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
            ProgressUtils.runBackground(project, "Translate", true) { progressIndicator ->
                progressIndicator.isIndeterminate = false
                val totalCount = 1.0 + otherStringsFile.size
                CoroutineScope(Dispatchers.Default).launch launch2@{
                    val enTranslate = TranslateUtils.translate(translateText, "en")
                    val key = TranslateUtils.mapStrToKey(enTranslate, isFormat)
                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    var current = 1.0
                    progressIndicator.fraction = current / totalCount * 0.95
                    otherStringsFile.forEach { file ->
                        if (file.targetLanguage == "en" && !isFormat) {
                            file.translate = enTranslate
                        } else {
                            file.translate =
                                TranslateUtils.translate(originalText, file.targetLanguage)
                            if (isFormat) {
                                file.translate = TranslateUtils.fixTranslateError(
                                    file.translate,
                                    file.targetLanguage,
                                    templateEntryList.size
                                )
                            }
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch2
                        }

                        current++
                        progressIndicator.fraction = current / totalCount * 0.95
                    }

                    if (progressIndicator.isCanceled) {
                        return@launch2
                    }

                    CoroutineScope(Dispatchers.EDT).launch {
                        var showRename = false
                        if (key == null || PluginSettings.getExtractStr2L10nShowRenameDialog(project)) {
                            showRename = true
                        } else {
                            if (xmlTag?.findFirstSubTag(key) != null) {
                                showRename = true
                            }
                        }

                        if (progressIndicator.isCanceled) {
                            return@launch
                        }

                        if (showRename) {
                            progressIndicator.fraction = 1.0
                            val newKey = renameKey(project, key, xmlTag, otherStringsFile)
                                ?: return@launch
                            WriteCommandAction.runWriteCommandAction(project) {
                                insertElement(project, stringsPsiFile, xmlTag, newKey, originalText)
                                replaceElement(
                                    selectedFile,
                                    selectedElement,
                                    templateEntryList,
                                    newKey
                                )
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
                                replaceElement(
                                    selectedFile,
                                    selectedElement,
                                    templateEntryList,
                                    key
                                )
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
                                progressIndicator.fraction = 1.0
                            }
                        }
                    }
                }
            }
        }
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

        val rootPanel: JPanel = if (otherStringsFile.size > 5) {
            builder.addComponentFillVertically(JPanel(), 0).panel
        } else {
            builder.panel
        }

        val jb = JBScrollPane(rootPanel)
        jb.preferredSize = JBUI.size(300, 40 + 60 * (otherStringsFile.size).coerceAtMost(5))
        jb.border = BorderFactory.createEmptyBorder()
        return jb
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

