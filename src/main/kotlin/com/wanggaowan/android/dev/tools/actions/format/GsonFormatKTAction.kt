package com.wanggaowan.android.dev.tools.actions.format

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.wanggaowan.android.dev.tools.ui.JsonToKotlinDialog
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * 使用gson格式化文本，生成对应Kotlin实体
 *
 * @author Created by wanggaowan on 2022/9/19 10:44
 */
class GsonFormatKTAction: AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        var psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement == null) {
            val editor = e.getData(CommonDataKeys.EDITOR)
            editor?.let {
                psiElement = findElementAtOffset(psiFile, it.selectionModel.selectionStart)
            }
        }

        val dialog = JsonToKotlinDialog(project, psiFile, psiElement)
        dialog.isVisible = true
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val language = e.getData(CommonDataKeys.LANGUAGE)
        e.presentation.isVisible = editor != null && language == KotlinLanguage.INSTANCE
    }

    /**
     * 查找指定下标位置element，如果找不到则往前一位查找，直到下标<0
     */
    private tailrec fun findElementAtOffset(psiFile: PsiFile, offset: Int): PsiElement? {
        if (offset < 0) {
            return null
        }
        val element = psiFile.findElementAt(offset)
        if (element != null) {
            return element
        }

        return findElementAtOffset(psiFile, offset - 1)
    }
}
