package com.wanggaowan.android.dev.tools.listener

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * 项目监听
 *
 * @author Created by wanggaowan on 2022/7/11 17:14
 */
class MyProjectManagerListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        super.projectClosing(project)
        // 项目关闭时清除copy缓存
        val basePath = project.basePath ?: return
        val projectRootFolder = VirtualFileManager.getInstance().findFileByUrl("file://${basePath}") ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val ideaFolder = projectRootFolder.findChild(".idea") ?: return@runWriteCommandAction
            val copyCacheFolder = ideaFolder.findChild("copyCache") ?: return@runWriteCommandAction
            copyCacheFolder.children?.forEach { it.delete(null) }
        }
    }
}
