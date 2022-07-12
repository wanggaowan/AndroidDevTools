package com.wanggaowan.android.dev.tools.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.android.dev.tools.utils.Toast


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
                val projectDir = project.basePath
                var selectFile: VirtualFile? = null
                if (projectDir != null) {
                    selectFile = VirtualFileManager.getInstance().findFileByUrl("file://${projectDir}/app/src/main/res")
                }

                if (selectFile == null) {
                    Toast.show(project, MessageType.ERROR, "不存在以下目录app/src/main/res")
                    return@chooseFiles
                }

                importImages(project, files, selectFile)
            }
        }
    }

    /**
     * 转化数据，获取所有需要导入的文件
     */
    private fun mapChosenFiles(selectedFiles: List<VirtualFile>): Map<String, MutableList<VirtualFile>> {
        // 将选中的文件加入列表，如果是目录则将目录下的文件加入列表
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
        val distinctFiles = distinctFile(dataList)
        val allFiles = mutableMapOf<String, MutableList<VirtualFile>>()
        // 获取不同分辨率下相同文件名称的图片
        distinctFiles.forEach {
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

    private fun importImages(project: Project, importFiles: List<VirtualFile>, importToFolder: VirtualFile) {
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
                mapFiles[folder.name]?.let {
                    it.forEach { child ->
                        child.copy(null, folder, child.name)
                    }
                }
            }
        }
    }
}
