package com.wanggaowan.android.dev.tools.ui

import java.awt.*
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO
import javax.swing.JPanel


/**
 * 展示图片控件
 *
 * @author Created by wanggaowan on 2022/6/20 08:44
 */
class ImageView(image: File? = null, val isDarkThem: Boolean = false) : JPanel() {

    private var image: Image? = null
    private var imgWidth = 0
    private var imgHeight = 0
    private var mBorderInsets = Insets(0, 0, 0, 0)

    init {
        image?.let { setImage(it) }
        preferredSize = Dimension(imgWidth, imgHeight)
    }

    fun setImage(file: File) {
        try { // 该方法会将图像加载到内存，从而拿到图像的详细信息。
            image = ImageIO.read(FileInputStream(file))
            imgWidth = image?.getWidth(null) ?: 0
            imgHeight = image?.getHeight(null) ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
        repaint()
    }


    override fun paint(g: Graphics) {
        super.paint(g)
        if (!isVisible) {
            return
        }

        if (g is Graphics2D) {
            // 消除画图锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        mBorderInsets.set(0, 0, 0, 0)
        if (border != null) {
            val insets = border.getBorderInsets(this)
            if (insets != null) {
                mBorderInsets.set(insets.top, insets.left, insets.bottom, insets.right)
            }
        }

        val width = width - mBorderInsets.left - mBorderInsets.right
        val height = height - mBorderInsets.top - mBorderInsets.bottom
        g.clipRect(mBorderInsets.left, mBorderInsets.top, width, height)

        drawBg(g)

        image?.let {
            if (imgWidth > 0 && imgHeight > 0 && width > 0 && height > 0) {
                val x: Int
                val y: Int
                val drawWidth: Int
                val drawHeight: Int
                if (imgWidth <= width && imgHeight <= height) {
                    val scale = (imgWidth * 1f / width).coerceAtLeast(imgHeight * 1f / height)

                    drawWidth = (imgWidth / scale).toInt()
                    drawHeight = (imgHeight / scale).toInt()
                    x = (width - drawWidth) / 2 + mBorderInsets.left
                    y = (height - drawHeight) / 2 + mBorderInsets.top
                } else {
                    val scale = (imgWidth * 1f / width).coerceAtLeast(imgHeight * 1f / height)
                    drawWidth = (imgWidth / scale).toInt()
                    drawHeight = (imgHeight / scale).toInt()
                    x = (width - drawWidth) / 2 + mBorderInsets.left
                    y = (height - drawHeight) / 2 + mBorderInsets.top
                }

                g.drawImage(image, x, y, drawWidth, drawHeight, null)
            }
        }
    }

    /**
     * 绘制明暗相间的网格底图
     */
    private fun drawBg(g: Graphics) {
        val rows = (height - mBorderInsets.top - mBorderInsets.bottom) / 10 + 1
        val columns = (width - mBorderInsets.left - mBorderInsets.right) / 10 / 2 + 1
        var yOffset = mBorderInsets.top
        var color1: Color
        var color2: Color
        for (row in 0 until rows) {
            if (row % 2 == 0) {
                color1 = if (isDarkThem) Color(57, 58, 59) else Color(205, 205, 205)
                color2 = if (isDarkThem) Color(65, 66, 67) else Color(240, 240, 240)
            } else {
                color2 = if (isDarkThem) Color(57, 58, 59) else Color(205, 205, 205)
                color1 = if (isDarkThem) Color(65, 66, 67) else Color(240, 240, 240)
            }

            var startOffset = mBorderInsets.left
            for (column in 0 until columns) {
                g.color = color1
                g.fillRect(startOffset, yOffset, 10, 10)
                g.color = color2
                g.fillRect(startOffset + 10, yOffset, 10, 10)
                startOffset += 20
            }

            yOffset += 10
        }
    }
}
