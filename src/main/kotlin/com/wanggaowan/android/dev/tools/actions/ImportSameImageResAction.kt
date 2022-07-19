package com.wanggaowan.android.dev.tools.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.android.dev.tools.ui.ImportImageFolderChooser
import com.wanggaowan.android.dev.tools.utils.NotificationUtils


/**
 * 导入不同分辨率相同图片资源
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
        FileChooser.chooseFiles(descriptor, project, null) { files ->
            if (!files.isNullOrEmpty()) {
                val distinctFiles = getDistinctFiles(files)
                val projectDir = project.basePath
                var selectFile: VirtualFile? = null
                if (projectDir != null) {
                    selectFile =
                        VirtualFileManager.getInstance().findFileByUrl("file://${projectDir}/app/src/main/res")
                }

                val dialog = ImportImageFolderChooser(project, "选择导入的文件夹", selectFile, distinctFiles)
                dialog.setOkActionListener {
                    val file = dialog.getSelectedFolder() ?: return@setOkActionListener
                    importImages(project, distinctFiles, file, dialog.getRenameFileMap())
                }
                dialog.isVisible = true
            }
        }
    }

    /**
     * 获取选择的文件去重后的数据
     */
    private fun getDistinctFiles(selectedFiles: List<VirtualFile>): List<VirtualFile> {
        val dataList = mutableListOf<VirtualFile>()
        selectedFiles.forEach {
            if (it.isDirectory) {
                it.children?.forEach { child ->
                    if (!child.isDirectory) {
                        dataList.add(child)
                    }
                }
            } else {
                dataList.add(it)
            }
        }

        // 去重，每个文件名称只保留一个数据
        return distinctFile(dataList)
    }

    /**
     * 转化数据，获取所有需要导入的文件
     */
    private fun mapChosenFiles(selectedFiles: List<VirtualFile>): Map<String, MutableList<VirtualFile>> {
        val allFiles = mutableMapOf<String, MutableList<VirtualFile>>()
        // 获取不同分辨率下相同文件名称的图片
        selectedFiles.forEach {
            it.parent.parent.children?.forEach { child ->
                if ((child.name.contains("drawable") && it.path.contains("drawable"))
                    || child.name.contains("mipmap") && it.path.contains("mipmap")
                ) {
                    child.findChild(it.name)?.let { file ->
                        var list = allFiles[child.name]
                        if (list == null) {
                            list = mutableListOf()
                            allFiles[child.name] = list
                        }

                        list.add(file)
                    }
                }
            }
        }
        return allFiles
    }

    /**
     * 去除重复的数据
     */
    private fun distinctFile(dataList: List<VirtualFile>): List<VirtualFile> {
        val list = mutableListOf<VirtualFile>()
        dataList.forEach {
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

    private fun importImages(
        project: Project, importFiles: List<VirtualFile>,
        importToFolder: VirtualFile, renameMap: Map<String, Map<String, String>>
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val mapFiles = mapChosenFiles(importFiles)
            val folders: LinkedHashSet<VirtualFile> = LinkedHashSet()
            val importDstFolders = importToFolder.children
            mapFiles.keys.forEach {
                var exist = false
                if (importDstFolders != null) {
                    for (importDstFolder in importDstFolders) {
                        if (importDstFolder.isDirectory && importDstFolder.name == it) {
                            exist = true
                            folders.add(importDstFolder)
                            break
                        }
                    }
                }

                if (!exist) {
                    try {
                        folders.add(importToFolder.createChildDirectory(null, it))
                    } catch (e: Exception) {
                        return@runWriteCommandAction
                    }
                }
            }

            folders.forEach { folder ->
                val mapFolder = if (folder.name.contains("drawable")) "Drawable"
                else if (folder.name.contains("mipmap")) {
                    "Mipmap"
                } else {
                    ""
                }

                val map = renameMap[mapFolder]
                mapFiles[folder.name]?.let {
                    it.forEach { child ->
                        try {
                            child.copy(null, folder, map?.get(child.name) ?: child.name)
                        } catch (e: Exception) {
                            // 可能是导入文件以及存在
                        }
                    }
                }
            }

            NotificationUtils.showBalloonMsg(project, "图片已导入", NotificationType.INFORMATION)
        }
    }
}
