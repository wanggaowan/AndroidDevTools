package com.wanggaowan.android.dev.tools.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * 后台进度辅助类
 *
 * @author Created by wanggaowan on 2023/12/25 09:14
 */
class ProgressHelper(val myProject: Project) {
    val myTasks: MutableList<String> = java.util.ArrayList()
    private var myTask: Task.Backgroundable? = null

    /**
     * Start a progress task.
     *
     * @param log the title of the progress task
     */
    fun start(log: String) {
        synchronized(myTasks) {
            myTasks.add(log)
            (myTasks as Object).notifyAll()
            if (myTask == null) {
                myTask = object : Task.Backgroundable(myProject, log, false) {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.text = log

                        synchronized(myTasks) {
                            while (myTasks.isNotEmpty()) {
                                indicator.text = myTasks[myTasks.size - 1]

                                try {
                                    (myTasks as Object).wait()
                                } catch (_: InterruptedException) {
                                    // ignore
                                }
                                myTask = null
                            }
                        }
                    }
                }

                ApplicationManager.getApplication().invokeLater {
                    synchronized(myTasks) {
                        if (myTask != null && !myProject.isDisposed) {
                            ProgressManager.getInstance().run(myTask!!)
                        }
                    }
                }
            }
        }
    }

    /**
     * Notify that a progress task has finished.
     */
    fun done() {
        synchronized(myTasks) {
            if (myTasks.isNotEmpty()) {
                myTasks.removeAt(myTasks.size - 1)
                (myTasks as Object).notifyAll()
            }
        }
    }

    /**
     * Finish any outstanding progress tasks.
     */
    fun cancel() {
        synchronized(myTasks) {
            myTasks.clear()
            (myTasks as Object).notifyAll()
        }
    }
}
