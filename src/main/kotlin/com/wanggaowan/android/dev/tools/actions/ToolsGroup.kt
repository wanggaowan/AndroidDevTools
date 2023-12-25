package com.wanggaowan.android.dev.tools.actions

import com.intellij.openapi.actionSystem.*
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject

/**
 * 工具栏Action分组
 *
 * @author Created by wanggaowan on 2023/12/25 11:12
 */
class ToolsGroup:DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(LangDataKeys.PROJECT)
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        if (!project.isAndroidProject) {
            e.presentation.isVisible = false
            return
        }

        e.presentation.isVisible = true
    }
}
