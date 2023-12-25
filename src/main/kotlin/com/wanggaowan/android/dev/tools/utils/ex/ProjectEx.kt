package com.wanggaowan.android.dev.tools.utils.ex

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.util.AndroidUtils

/*
 * Project扩展
 */

// <editor-fold desc="project">

fun Project?.getModules(): Array<Module>? {
    if (this == null) {
        return null
    }
    return ModuleManager.getInstance(this).modules
}

/**
 * [Project]根目录
 */
val Project.rootDir: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl("file://${this.basePath}")

/**
 * 获取项目根目录下的指定[name]文件
 */
fun Project.findChild(name: String) = rootDir?.findChild(name)

val Project.isAndroidProject: Boolean
    get() {
        return AndroidUtils.getInstance().isAndroidProject(this)
    }

/// 图片资源根目录
val Project.imageRootDir: VirtualFile?
    get() {
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${basePath}/app/src/main/res")
    }
// </editor-fold>

