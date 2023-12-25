package com.wanggaowan.android.dev.tools.listener

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.wanggaowan.android.dev.tools.utils.TempFileUtils

private val LOG = logger<MyProjectManagerListener>()

/**
 * 项目监听
 *
 * @author Created by wanggaowan on 2022/7/11 17:14
 */
class MyProjectManagerListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        super.projectClosing(project)
        if (project.isDisposed) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                TempFileUtils.clearCopyCacheFolder(project)
            } catch (e: Exception) {
                LOG.error(e.message)
            }

            try {
                TempFileUtils.clearUnZipCacheFolder(project)
            } catch (e: Exception) {
                LOG.error(e.message)
            }
        }
    }
}
