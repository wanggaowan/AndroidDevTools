package com.wanggaowan.android.dev.tools.utils

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 *
 *
 * @author Created by wanggaowan on 2024/1/5 15:28
 */
object ProgressUtils {
    fun runBackground(
        project: Project,
        title: String,
        canBeCancelled: Boolean = false,
        run: (progressIndicator: ProgressIndicator) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
            override fun run(p0: ProgressIndicator) {
                run(p0)
                while (!p0.isCanceled && p0.fraction < 1) {
                    Thread.sleep(100)
                }
            }
        })
    }
}
