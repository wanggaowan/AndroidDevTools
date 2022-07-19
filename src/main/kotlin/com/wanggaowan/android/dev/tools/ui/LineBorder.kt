package com.wanggaowan.android.dev.tools.ui

import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.border.AbstractBorder

/**
 * 绘制线性边框
 *
 * @author Created by wanggaowan on 2022/6/17 15:42
 */
class LineBorder constructor(
    /**
     * 线条颜色
     */
    private val color: Color,
    /**
     * 顶部线条宽度，为0则不绘制
     */
    private var topWidth: Int,
    /**
     * 左边线条宽度，为0则不绘制
     */
    private var leftWidth: Int,
    /**
     * 底部线条宽度，为0则不绘制
     */
    private var bottomWidth: Int,
    /**
     * 右边线条宽度，为0则不绘制
     */
    private var rightWidth: Int,
) : AbstractBorder() {

    private var roundedCorners: Boolean = false

    @JvmOverloads
    constructor(
        /**
         * 线条颜色
         */
        color: Color,
        /**
         * 线条宽度
         */
        width: Int = 1,
        roundedCorners: Boolean = false,
    ) : this(color, width, width, width, width) {
        this.roundedCorners = roundedCorners
    }


    override fun paintBorder(component: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
        if (g is Graphics2D) {
            // 消除画图锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val oldColor = g.color
            g.color = color
            val outer: Any
            val inner: Any
            if (roundedCorners) {
                outer = RoundRectangle2D.Float(
                    x.toFloat(),
                    y.toFloat(),
                    width.toFloat(),
                    height.toFloat(),
                    2f,
                    2f,
                )

                inner = RoundRectangle2D.Float(
                    (x + leftWidth).toFloat(),
                    (y + topWidth).toFloat(),
                    (width - leftWidth - rightWidth).toFloat(),
                    (height - topWidth - bottomWidth).toFloat(),
                    2f,
                    2f,
                )
            } else {
                outer = Rectangle2D.Float(
                    x.toFloat(),
                    y.toFloat(),
                    width.toFloat(),
                    height.toFloat(),
                )

                inner = Rectangle2D.Float(
                    (x + leftWidth).toFloat(),
                    (y + topWidth).toFloat(),
                    (width - leftWidth - rightWidth).toFloat(),
                    (height - topWidth - bottomWidth).toFloat(),
                )
            }

            val path: Path2D = Path2D.Float(0)
            path.append(outer as Shape, false)
            path.append(inner as Shape, false)
            g.fill(path)
            g.color = oldColor
        }
    }

    override fun getBorderInsets(c: Component?, insets: Insets): Insets {
        insets.left = leftWidth
        insets.top = topWidth
        insets.right = rightWidth
        insets.bottom = bottomWidth
        return insets
    }

    override fun isBorderOpaque(): Boolean {
        return !roundedCorners
    }
}
