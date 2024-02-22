package com.wanggaowan.android.dev.tools.utils.ex

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.konan.file.File

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

/// 资源根目录
val Project.resRootDir: VirtualFile?
    get() {
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${basePath}/app/src/main/res")
    }
// </editor-fold>

// <editor-fold desc="Module">
/**
 * 是否是Flutter项目
 */
val Module?.isAndroidProject: Boolean
    get() {
        if (this == null) {
            return false
        }
        return AndroidUtils.getInstance().isAndroidProject(project)
    }

val Module?.rootDir: VirtualFile?
    get() {
        val path = basePath ?: return null
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${path}")
    }

val Module?.basePath: String?
    get() {
        if (this == null) {
            return null
        }


        val names = this.name.split(".")
        if (names.isEmpty()) {
            return null
        }

        if (names.size <= 1) {
            return null
        }

        return this.project.basePath + File.separator + names[1]
    }

/**
 * 获取模块根目录下的指定[name]文件
 */
fun Module.findChild(name: String) = rootDir?.findChild(name)

/// 资源根目录
val Module.resRootDir: VirtualFile?
    get() {
        return VirtualFileManager.getInstance()
            .findFileByUrl("file://${basePath}/src/main/res")
    }
// </editor-fold>

