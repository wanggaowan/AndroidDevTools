package com.wanggaowan.android.dev.tools.ui

import com.intellij.util.ui.JBInsets
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * 展示一个一像素的线条
 *
 * @author Created by wanggaowan on 2023/8/14 13:45
 */
class JLine(color: Color, margin: JBInsets? = null) : JComponent() {
    private var color: Color = UIColor.LINE_COLOR
    private var margin: JBInsets?

    init {
        this.color = color
        this.margin = margin
    }

    fun setColor(color: Color) {
        this.color = color
        repaint()
    }

    fun setMargin(margin: JBInsets?) {
        this.margin = margin
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

        g.color = color
        val height = height / 2
        g.drawLine(0 + (margin?.left ?: 0), height, width - (margin?.right ?: 0), height)
    }
}
