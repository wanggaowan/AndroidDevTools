package com.wanggaowan.android.dev.tools.utils

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Zip压缩包工具类
 *
 * @author Created by wanggaowan on 2023/11/14 17:16
 */
object ZipUtil {
    fun unzip(zipFile: String, descDir: String): File? {
        val buffer = ByteArray(1024)
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null
        try {
            val zf = ZipFile(zipFile)
            var folderName = zf.name
            var index = folderName.lastIndexOf(".")
            if (index != -1) {
                folderName = folderName.substring(0, index)
            }
            index = folderName.lastIndexOf(File.separator)
            if (index != -1) {
                folderName = folderName.substring(index + 1)
            }

            val parentDir = descDir + File.separator + folderName
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val zipEntry: ZipEntry = entries.nextElement() as ZipEntry
                val zipEntryName: String = zipEntry.name
                if (zipEntryName.startsWith("__MACOSX")) {
                    continue
                }

                if (zipEntry.isDirectory) {
                    createDir(parentDir + File.separator + zipEntryName)
                    continue
                }

                val descFile = createFile(parentDir + File.separator + zipEntryName) ?: continue
                inputStream = zf.getInputStream(zipEntry)
                outputStream = FileOutputStream(descFile)
                var len: Int
                while (inputStream.read(buffer).also { len = it } > 0) {
                    outputStream.write(buffer, 0, len)
                }
                inputStream.closeQuietly()
                outputStream.closeQuietly()
            }
            return File(parentDir)
        } catch (_: Exception) {
            return null
        } finally {
            inputStream?.closeQuietly()
            outputStream?.closeQuietly()
        }
    }

    private fun createDir(dirPath: String): File? {
        val file = File(dirPath)
        if (!file.exists() && !file.mkdirs()) {
            return null
        }
        return file
    }

    private fun createFile(filePath: String): File? {
        val file = File(filePath)
        val parentFile = file.parentFile ?: return null
        if (!parentFile.exists() && !parentFile.mkdirs()) {
            return null
        }

        if (!file.exists() && !file.createNewFile()) {
            return null
        }
        return file
    }

    fun zip(files: List<File>, zipFilePath: String): File? {
        if (files.isEmpty()) return null

        val zipFile = createFile(zipFilePath) ?: return null
        val buffer = ByteArray(1024)
        var zipOutputStream: ZipOutputStream? = null
        var inputStream: FileInputStream? = null
        try {
            zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))
            for (file in files) {
                if (!file.exists()) continue
                zipOutputStream.putNextEntry(ZipEntry(file.name))
                inputStream = FileInputStream(file)
                var len: Int
                while (inputStream.read(buffer).also { len = it } > 0) {
                    zipOutputStream.write(buffer, 0, len)
                }
                zipOutputStream.closeEntry()
            }
            return zipFile
        } catch (_: Exception) {
            return null
        } finally {
            inputStream?.close()
            zipOutputStream?.close()
        }
    }

    fun zipByFolder(fileDir: String, zipFilePath: String): File? {
        val folder = File(fileDir)
        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()
            val filesList: List<File>? = files?.toList()
            if (filesList.isNullOrEmpty()) {
                return null
            }

            return zip(filesList, zipFilePath)
        }
        return null
    }
}

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: Exception) {
        //
    }
}
