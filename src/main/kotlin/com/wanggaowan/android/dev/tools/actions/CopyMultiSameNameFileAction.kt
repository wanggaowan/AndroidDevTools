package com.wanggaowan.android.dev.tools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.awt.Toolkit
import java.io.File

/**
 * 复制多个相同名称但是在不同分辨率下的文件
 *
 * @author Created by wanggaowan on 2022/7/11 13:47
 */
class CopyMultiSameNameFileAction : AnAction() {
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

            var drawableFolders: MutableList<String>? = null
            val drawableCopyFolders: MutableList<VirtualFile> = mutableListOf()
            var mipmapFolders: MutableList<String>? = null
            val mipmapCopyFolders: MutableList<VirtualFile> = mutableListOf()

            distinctFile(selectFiles).forEach {
                if (!it.isDirectory) {
                    val isDrawable = it.path.contains("drawable")
                    val isMipmap = it.path.contains("mipmap")
                    if (isDrawable || isMipmap) {
                        if (isDrawable && drawableFolders == null) {
                            drawableFolders = mutableListOf()
                            it.parent.parent.children?.forEach { child ->
                                if (child.name.contains("drawable")) {
                                    drawableFolders?.add(child.name)
                                    try {
                                        drawableCopyFolders.add(copyCacheFolder.createChildDirectory(null, child.name))
                                    } catch (e: Exception) {
                                        return@runWriteCommandAction
                                    }
                                }
                            }
                        }

                        if (isMipmap && mipmapFolders == null) {
                            mipmapFolders = mutableListOf()
                            it.parent.parent.children?.forEach { child ->
                                if (child.name.contains("mipmap")) {
                                    mipmapFolders?.add(child.name)
                                    try {
                                        mipmapCopyFolders.add(copyCacheFolder.createChildDirectory(null, child.name))
                                    } catch (e: Exception) {
                                        return@runWriteCommandAction
                                    }
                                }
                            }
                        }

                        if (isDrawable) {
                            copyFileToCacheFolder(it, drawableFolders, drawableCopyFolders)
                        } else {
                            copyFileToCacheFolder(it, mipmapFolders, mipmapCopyFolders)
                        }
                    }
                }
            }

            val needCopyFile = mutableListOf<File>()
            copyCacheFolder.children?.forEach {
                if (!it.isDirectory || (it.children != null && it.children.isNotEmpty())) {
                    needCopyFile.add(File(it.path))
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileTransferable(needCopyFile), null)
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (virtualFiles.isNullOrEmpty()) {
            e.presentation.isVisible = false
            return
        }

        for (file in virtualFiles) {
            if (!file.isDirectory) {
                e.presentation.isVisible = true
                return
            }
        }

        e.presentation.isVisible = false
    }

    /**
     * 复制需要粘贴的文件到临时的缓存目录
     */
    private fun copyFileToCacheFolder(
        file: VirtualFile,
        folderList: List<String>?,
        copyFolderList: List<VirtualFile>
    ) {
        if (folderList.isNullOrEmpty()) {
            return
        }

        val parentPath = file.parent.parent.path
        val fileName = file.name
        folderList.indices.forEach {
            val folderName = folderList[it]
            val child = VirtualFileManager.getInstance().findFileByUrl("file://${parentPath}/$folderName/$fileName")
            child?.copy(null, copyFolderList[it], fileName)
        }
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(array: Array<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        array.forEach {
            var exist = false
            val isDrawable = it.path.contains("drawable")
            val isMipmap = it.path.contains("mipmap")
            for (file in list) {
                if (file.name == it.name) {
                    if (file.path.contains("drawable") && isDrawable) {
                        exist = true
                        break
                    } else if (file.path.contains("mipmap") && isMipmap) {
                        exist = true
                        break
                    }
                }
            }

            if (!exist) {
                list.add(it)
            }
        }
        return list
    }
}
