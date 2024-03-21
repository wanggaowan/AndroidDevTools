package com.wanggaowan.android.dev.tools.ui.language

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.json.JsonLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField

/**
 * JSON语言文本编辑器，提供语法高亮及只能补全等
 *
 * @author Created by wanggaowan on 2024/3/21 14:12
 */
class JsonLanguageTextField(project: Project) :
    LanguageTextField(JsonLanguage.INSTANCE, project, "", false) {

    override fun createEditor(): EditorEx {
        val editorEx = super.createEditor()
        editorEx.setVerticalScrollbarVisible(true)
        editorEx.setHorizontalScrollbarVisible(true)
        editorEx.setCaretEnabled(true)
        editorEx.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (editorEx.isDisposed) {
                    return
                }

                ApplicationManager.getApplication().invokeLater({
                    if (editorEx.isDisposed) {
                        return@invokeLater
                    }
                    CodeFoldingManager.getInstance(this@JsonLanguageTextField.project).updateFoldRegions(editorEx)
                }, ModalityState.NON_MODAL)
            }
        })

        val settings: EditorSettings = editorEx.settings
        settings.isLineNumbersShown = true
        settings.isUseSoftWraps = false
        settings.isAutoCodeFoldingEnabled = true
        settings.isFoldingOutlineShown = true
        settings.isAllowSingleLogicalLineFolding = true
        settings.additionalLinesCount = 5
        return editorEx
    }
}
