package com.wanggaowan.android.dev.tools.actions.translate

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlTag
import com.intellij.util.LocalTimeCounter
import com.wanggaowan.android.dev.tools.utils.NotificationUtils
import com.wanggaowan.android.dev.tools.utils.TranslateUtils
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 翻译strings文件
 *
 * @author Created by wanggaowan on 2024/1/4 17:04
 */
class TranslateStringsAction : DumbAwareAction() {

    private var templateFile: VirtualFile? = null
    private var file: VirtualFile? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        file = null
        templateFile = null
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }
        if (!project.isAndroidProject) {
            e.presentation.isVisible = false
            return
        }

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || file.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (file.name != "strings.xml") {
            e.presentation.isVisible = false
            return
        }

        if (file.parent?.name?.startsWith("values-") != true) {
            e.presentation.isVisible = false
            return
        }

        if (file.parent?.parent?.name != "res") {
            e.presentation.isVisible = false
            return
        }

        val valuesDir = file.parent?.parent?.findChild("values")
        if (valuesDir != null && valuesDir.isDirectory) {
            val strings = valuesDir.findChild("strings.xml")
            if (strings != null && !strings.isDirectory) {
                this.templateFile = strings
            }
        }

        this.file = file
        e.presentation.isVisible = true
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (file == null) {
            return
        }

        val project = event.project ?: return
        if (templateFile == null) {
            NotificationUtils.showBalloonMsg(project, "请提供模版文件values/strings.xml", NotificationType.WARNING)
            return
        }

        val tempStringsPsiFile = templateFile!!.toPsiFile(project) ?: return
        val stringsPsiFile = file!!.toPsiFile(project) ?: return
        val tempXmlTags = tempStringsPsiFile.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()?.subTags
            ?: return
        val xmlTag = stringsPsiFile.getChildOfType<XmlDocument>()?.getChildOfType<XmlTag>()
        val xmlTags = xmlTag?.subTags ?: arrayOf()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translate") {
            var finish = false
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = false
                ApplicationManager.getApplication().runReadAction {
                    val needTranslateMap = mutableMapOf<String, String?>()
                    tempXmlTags.forEach {
                        val find = xmlTags.find { tag -> it.getAttributeValue("name") == tag.getAttributeValue("name") }
                        if (find == null) {
                            val name = it.getAttributeValue("name")
                            if (name != null) {
                                needTranslateMap[name] = it.value.text
                            }
                        }
                    }

                    if (needTranslateMap.isEmpty()) {
                        finish = true
                        return@runReadAction
                    }

                    progressIndicator.fraction = 0.05
                    var existTranslateFailed = false
                    CoroutineScope(Dispatchers.Default).launch launch2@{
                        val targetLanguage = file!!.parent.name.substring("values-".length)
                        var count = 1.0
                        val total = needTranslateMap.size
                        needTranslateMap.forEach { (key, value) ->
                            var translateStr = TranslateUtils.translate(value!!, targetLanguage)
                            progressIndicator.fraction = count / total * 0.94 + 0.05
                            if (translateStr == null) {
                                existTranslateFailed = true
                                needTranslateMap[key] = null
                            } else {
                                translateStr = TranslateUtils.fixTranslateError(translateStr, targetLanguage)
                                needTranslateMap[key] = translateStr ?: ""
                            }
                            count++
                        }

                        CoroutineScope(Dispatchers.Main).launch {
                            WriteCommandAction.runWriteCommandAction(project) {
                                val document = stringsPsiFile.viewProvider.document
                                if (document != null) {
                                    PsiDocumentManager.getInstance(project).commitDocument(document)
                                }
                                needTranslateMap.forEach { (key, value) ->
                                    if (value != null) {
                                        insertElement(project, stringsPsiFile, xmlTag, key, value)
                                    }
                                }
                                if (document != null) {
                                    PsiDocumentManager.getInstance(project).commitDocument(document)
                                } else {
                                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                                }
                                if (existTranslateFailed) {
                                    NotificationUtils.showBalloonMsg(project, "部分内容未翻译或插入成功，请重试", NotificationType.WARNING)
                                }
                                progressIndicator.fraction = 1.0
                                finish = true
                            }
                        }
                    }
                }

                while (!finish && !progressIndicator.isCanceled) {
                    Thread.sleep(100)
                }
            }
        })
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
    }
}
