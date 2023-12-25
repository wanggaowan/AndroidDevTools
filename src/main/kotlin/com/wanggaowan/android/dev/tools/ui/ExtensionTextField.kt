package com.wanggaowan.android.dev.tools.ui

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints


/**
 * TextField扩展类
 *
 * @author Created by wanggaowan on 2023/11/27 09:36
 */
class ExtensionTextField(text: String? = null, columns: Int = 0, var placeHolder: String? = null) : JBTextField(text, columns) {

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        if (placeHolder.isNullOrEmpty() || !text.isNullOrEmpty()) {
            return
        }

        var width = width
        if (width <= 0) {
            return
        }

        var height = height
        if (height <= 0) {
            return
        }

        val margin = margin
        var x = margin.left
        var y = margin.top
        width -= margin.left + margin.right
        height -= margin.top + margin.bottom
        val border = border
        if (border != null) {
            val instes = border.getBorderInsets(this)
            x += instes.left
            width -= instes.left + instes.right
            y += instes.top
            height -= instes.top + instes.bottom
        }

        if (width <= 0) {
            return
        }

        if (height <= 0) {
            return
        }

        val pG = g as Graphics2D
        pG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        pG.color = disabledTextColor
        UIUtil.drawCenteredString(pG, Rectangle(x, y, width, height), placeHolder!!, false, true)

        // val fm = pG.getFontMetrics(pG.font)
        // val insets = insets
        // val margin = margin
        // val top = insets.top + margin.top
        // val y = top.coerceAtLeast((top + (insets.bottom - insets.top) / 2 + fm.ascent * 2 / 5))
        // pG.drawString(placeHolder!!, insets.left + margin.left, y)
    }
}
