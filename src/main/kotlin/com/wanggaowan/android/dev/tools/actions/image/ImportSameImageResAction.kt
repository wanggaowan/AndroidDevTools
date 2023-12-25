package com.wanggaowan.android.dev.tools.actions.image

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.wanggaowan.android.dev.tools.ui.ImportImageFolderChooser
import com.wanggaowan.android.dev.tools.ui.RenameEntity
import com.wanggaowan.android.dev.tools.utils.*


/**
 * 导入不同分辨率相同图片资源
 *
 * @author Created by wanggaowan on 2022/7/12 09:42
 */
class ImportSameImageResAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, true, true, true, false, true)
        FileChooser.chooseFiles(descriptor, project, null) { files ->
            if (!files.isNullOrEmpty()) {
                val importToFolder = VirtualFileManager.getInstance()
                        .findFileByUrl("file://${project.basePath}/app/src/main/res")
                ImportSameImageResUtils.import(project, files, importToFolder)
            }
        }
    }
}

object ImportSameImageResUtils{
    fun import(
        project: Project,
        files: List<VirtualFile>,
        importToFolder: VirtualFile? = null,
        doneCallback: (() -> Unit)? = null
    ) {
        val progressHelper = ProgressHelper(project)
        progressHelper.start("parse image data")
        val distinctFiles = getDistinctFiles(project, files)
        progressHelper.done()
        var selectFile: VirtualFile? = importToFolder
        if (selectFile == null) {
            selectFile =
                VirtualFileManager.getInstance().findFileByUrl("file://${project.basePath}/app/src/main/res")
        }

        val dialog = ImportImageFolderChooser(project, "导入图片", selectFile, distinctFiles)
        dialog.setOkActionListener {
            val file = dialog.getSelectedFolder()
            if (file == null) {
                TempFileUtils.clearUnZipCacheFolder(project)
                return@setOkActionListener
            }

            importImages(project, distinctFiles, file, dialog.getRenameFileMap(), doneCallback)
        }
        dialog.setCancelActionListener {
            TempFileUtils.clearUnZipCacheFolder(project)
        }
        dialog.isVisible = true
    }

    /**
     * 获取选择的文件去重后的数据
     */
    private fun getDistinctFiles(project: Project,selectedFiles: List<VirtualFile>): List<VirtualFile> {
        val dataList = mutableListOf<VirtualFile>()
        selectedFiles.forEach {
            if (it.isDirectory) {
                val dirName = it.name
                it.children?.forEach { child ->
                    val name = child.name
                    if (!name.startsWith(".")) {
                        if (!child.isDirectory) {
                            if (it.name.lowercase().endsWith(".zip")) {
                                parseZipFile(project, it, dirName, dataList)
                            } else if (fileCouldAdd(child)) {
                                dataList.add(child)
                            }
                        } else if (
                        // 当前child父对象不是drawable或mipmap开头，只解析这个目录中的图片，不解析目录
                            (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) &&
                            (name.startsWith("drawable") || name.startsWith("mipmap"))
                        ) {
                            // 只解析两层目录
                            child.children?.forEach { child2 ->
                                if (fileCouldAdd(child2)) {
                                    dataList.add(child2)
                                }
                            }
                        }
                    }
                }
            } else if (it.name.lowercase().endsWith(".zip")) {
                parseZipFile(project, it, "", dataList)
            } else if (fileCouldAdd(it)) {
                dataList.add(it)
            }
        }

        // 去重，每个文件名称只保留一个数据
        return distinctFile(dataList)
    }

    private fun parseDirectory(directory: VirtualFile, dataList: MutableList<VirtualFile>) {
        val dirName = directory.name
        directory.children?.forEach { child ->
            val name = child.name
            if (!name.startsWith(".")) {
                if (!child.isDirectory) {
                    if (fileCouldAdd(child)) {
                        dataList.add(child)
                    }
                } else if (
                // 当前child父对象不是drawable或mipmap开头，只解析这个目录中的图片，不解析目录
                    (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) &&
                    (name.startsWith("drawable") || name.startsWith("mipmap"))
                ) {
                    // 只解析两层目录
                    child.children?.forEach { child2 ->
                        if (fileCouldAdd(child2)) {
                            dataList.add(child2)
                        }
                    }
                }
            }
        }
    }

    private fun parseZipFile(
        project: Project,
        file: VirtualFile,
        parentName: String,
        dataList: MutableList<VirtualFile>
    ) {
        val folder = TempFileUtils.getUnZipCacheFolder(project)
        if (folder != null) {
            val descDir = if (parentName.isNotEmpty()) {
                folder.path + parentName
            } else {
                folder.path
            }

            val unZipFile = ZipUtil.unzip(file.path, descDir) ?: return
            val directory =
                VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${unZipFile.path}") ?: return
            parseDirectory(directory, dataList)
        }
    }

    private fun fileCouldAdd(file: VirtualFile): Boolean {
        if (file.isDirectory) {
            return false
        }

        val name = file.name
        if (name.startsWith(".")) {
            return false
        }

        if (!XUtils.isImage(name)) {
            return false
        }

        val parent = file.parent ?: return false
        val parentName = parent.name
        return parentName.startsWith("drawable") || parentName.startsWith("mipmap")
    }

    private fun importImages(
        project: Project, importFiles: List<VirtualFile>,
        importToFolder: VirtualFile, renameMap: Map<String, List<RenameEntity>>,
        doneCallback: (() -> Unit)? = null
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

                val renameList = renameMap[mapFolder]
                mapFiles[folder.name]?.let {
                    it.forEach { child ->
                        try {
                            var renameEntity: RenameEntity? = null
                            if (renameList != null) {
                                for (rename in renameList) {
                                    if (rename.oldName == child.name) {
                                        renameEntity = rename
                                        break
                                    }
                                }
                            }

                            if (renameEntity != null && renameEntity.existFile && renameEntity.coverExistFile) {
                                // 删除已经存在的文件
                                folder.findChild(renameEntity.newName)?.delete(project)
                            }

                            child.copy(project, folder, renameEntity?.newName ?: child.name)
                        } catch (e: Exception) {
                            // 可能是导入文件已经存在
                        }
                    }
                }
            }

            NotificationUtils.showBalloonMsg(project, "图片已导入", NotificationType.INFORMATION)
            doneCallback?.invoke()
        }
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
}
