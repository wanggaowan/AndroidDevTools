package com.wanggaowan.android.dev.tools.actions

import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlDocument
import com.intellij.psi.xml.XmlTag
import com.wanggaowan.android.dev.tools.utils.ProgressUtils
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * 删除多个strings文件相同key元素
 *
 * @author Created by wanggaowan on 2024/4/1 17:03
 */
class DeleteStringsSameKeyElementAction : DumbAwareAction() {
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

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null || file.isDirectory) {
            e.presentation.isVisible = false
            return
        }

        if (file.name != "strings.xml") {
            e.presentation.isVisible = false
            return
        }

        if (file.parent?.name?.startsWith("values") != true) {
            e.presentation.isVisible = false
            return
        }

        if (file.parent?.parent?.name != "res") {
            e.presentation.isVisible = false
            return
        }

        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (element !is ResourceReferencePsiElement && element !is XmlTag) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT)
        val xmlTag: PsiElement? =
            if (element is ResourceReferencePsiElement) {
                element.parent
            } else {
                element
            }

        if (xmlTag == null || xmlTag !is XmlTag) {
            return
        }

        val project = xmlTag.project
        ProgressUtils.runBackground(project, "delete strings same key") { indicator ->
            indicator.isIndeterminate = true
            WriteCommandAction.runWriteCommandAction(project) {
                val results = mutableListOf<PsiElement>()
                results.add(xmlTag)

                val name = xmlTag.getAttributeValue("name")?.trim()
                if (name != null) {
                    val file = xmlTag.containingFile?.virtualFile
                    var parent = file?.parent
                    if (parent != null && parent.isDirectory) {
                        // values目录
                        val folderName = parent.name
                        parent = parent.parent
                        if (parent != null && parent.isDirectory) {
                            // res目录
                            getOtherArbSameElement(project, parent, folderName, name, results)
                        }
                    }
                }


                results.forEach {
                    it.delete()
                }
                FileDocumentManager.getInstance().saveAllDocuments()
                indicator.fraction = 1.0
            }
        }
    }

    private fun getOtherArbSameElement(
        project: Project,
        parent: VirtualFile,
        currentFileFolder: String,
        key: String,
        results: MutableList<PsiElement>
    ) {
        parent.children.forEach loop1@{
            val name = it.name
            if (!it.isDirectory || !name.startsWith("values") || name == currentFileFolder) {
                return@loop1
            }

            val stringsFile = it.findChild("strings.xml") ?: return@loop1
            val subTags = stringsFile.toPsiFile(project)?.getChildOfType<XmlDocument>()
                ?.getChildOfType<XmlTag>()?.subTags?:return@loop1
            subTags.forEach {xmlTag->
                xmlTag.attributes.forEach {attrs->
                    if (attrs.name == "name" && attrs.value == key) {
                        results.add(xmlTag)
                        return@loop1
                    }
                }
            }
        }
    }
}
