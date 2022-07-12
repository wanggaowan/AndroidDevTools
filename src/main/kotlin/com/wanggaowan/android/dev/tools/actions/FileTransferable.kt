package com.wanggaowan.android.dev.tools.actions

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * 复制的文件数据
 */
class FileTransferable(private val files: List<*>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.javaFileListFlavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
        return DataFlavor.javaFileListFlavor.equals(flavor)
    }

    override fun getTransferData(flavor: DataFlavor?): Any {
        return if (!DataFlavor.javaFileListFlavor.equals(flavor)) {
            throw UnsupportedFlavorException(flavor)
        } else {
            files
        }
    }
}
