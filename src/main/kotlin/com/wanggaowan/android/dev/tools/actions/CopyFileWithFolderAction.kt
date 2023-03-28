package com.wanggaowan.android.dev.tools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.util.AndroidUtils
import java.awt.Toolkit
import java.io.File

/**
 * 带目录复制文件
 *
 * @author Created by wanggaowan on 2022/7/12 08:40
 */
class CopyFileWithFolderAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val projectRootFolder = VirtualFileManager.getInstance().findFileByUrl("file://${basePath}") ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            var ideaFolder = projectRootFolder.findChild(".idea")
            if (ideaFolder == null) {
                try {
                    ideaFolder = projectRootFolder.createChildDirectory(null, ".idea")
                } catch (e: Exception) {
                    return@runWriteCommandAction
                }
            }
            var copyCacheFolder = ideaFolder.findChild("copyCache")
            if (copyCacheFolder == null) {
                try {
                    copyCacheFolder = ideaFolder.createChildDirectory(null, "copyCache")
                } catch (e: Exception) {
                    return@runWriteCommandAction
                }
            }

            copyCacheFolder.children?.forEach { it.delete(null) }
            val selectFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return@runWriteCommandAction
            if (selectFiles.isEmpty()) {
                return@runWriteCommandAction
            }

            val mapFiles = mutableListOf<SelectedFile>()
            for (file in selectFiles) {
                if (file.isDirectory) {
                    continue
                }

                var path = file.path
                var indexOf = path.indexOf("drawable")
                var isDrawable = false
                var isMipmap = false
                if (indexOf != -1) {
                    isDrawable = true
                    path = path.substring(indexOf)
                } else {
                    indexOf = path.indexOf("mipmap")
                    if (indexOf != -1) {
                        isMipmap = true
                        path = path.substring(indexOf)
                    }
                }

                if (isDrawable || isMipmap) {
                    val split = path.split("/")
                    if (split.size == 2) {
                        mapFiles.add(SelectedFile(file, split[0]))
                    }
                }
            }

            if (mapFiles.isEmpty()) {
                return@runWriteCommandAction
            }

            val folders: List<VirtualFile> = getDistinctFolder(mapFiles).map {
                try {
                    copyCacheFolder.createChildDirectory(null, it)
                } catch (e: Exception) {
                    return@runWriteCommandAction
                }
            }

            mapFiles.forEach {
                for (folder in folders) {
                    if (folder.name == it.folder) {
                        it.file.copy(null, folder, it.file.name)
                        break
                    }
                }
            }

            val needCopyFile = mutableListOf<File>()
            copyCacheFolder.children?.forEach {
                if (!it.isDirectory || !it.children.isNullOrEmpty()) {
                    needCopyFile.add(File(it.path))
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileTransferable(needCopyFile), null)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        if (!AndroidUtils.hasAndroidFacets(project)) {
            return
        }

        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            return
        }


        val parent = virtualFiles[0].parent ?: return
        val name = parent.name
        if (!name.startsWith("drawable") || !name.startsWith("mipmap")) {
            return
        }

        for (file in virtualFiles) {
            if (file.isDirectory) {
                return
            }
        }

        e.presentation.isVisible = true
    }

    /**
     * 获取去重后的目录
     */
    private fun getDistinctFolder(files: List<SelectedFile>): List<String> {
        val list = mutableListOf<String>()
        files.forEach {
            var exist = false
            for (folder in list) {
                if (folder == it.folder) {
                    exist = true
                    break
                }
            }

            if (!exist) {
                list.add(it.folder)
            }
        }
        return list
    }

    data class SelectedFile(
        /**
         * 选中的文件
         */
        val file: VirtualFile,
        /**
         * 文件所在目录名称
         */
        val folder: String
    )
}
