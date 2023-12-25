package com.wanggaowan.android.dev.tools.ui

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

/**
 * UI颜色文件
 *
 * @author Created by wanggaowan on 2023/2/12 20:55
 */
object UIColor {
    // 控件设置JBColor后，可以自动根据当前主题来采用对应颜色

    /**
     * 分割线颜色
     */
    val LINE_COLOR = JBColor(Gray._209, Gray._81)

    /**
     * 鼠标进入颜色
     */
    val MOUSE_ENTER_COLOR = JBColor(Gray._223, Color(76, 80, 82))

    /**
     * 鼠标进入颜色,用于背景透明的Icon
     */
    val MOUSE_ENTER_COLOR2 = JBColor(Color(191, 197, 200), Color(98, 106, 110))

    /**
     * 鼠标按下颜色
     */
    val MOUSE_PRESS_COLOR = JBColor(Gray._207, Color(92, 97, 100))

    /**
     * 鼠标按下颜色，,用于背景透明的Icon
     */
    val MOUSE_PRESS_COLOR2 = JBColor(Color(162, 166, 169), Color(82, 87, 91))

    /**
     * 输入框获取焦点时的颜色
     */
    val INPUT_FOCUS_COLOR = JBColor(Color(71, 135, 201), Color(71, 135, 201))

    /**
     * 输入框未获取焦点时的颜色
     */
    val INPUT_UN_FOCUS_COLOR = JBColor(Gray._196, Gray._100)

    /**
     * 图片预览，网格预览模式下，展示图片路径Label的背景颜色
     */
    val IMAGE_TITLE_BG_COLOR = JBColor(Gray._252, Color(49, 52, 53))

    /**
     * 图片预览，网格预览模式下，展示图片路径Label的背景颜色
     */
    val BG_COLOR = JBColor(Color.WHITE, Color(60, 63, 65))

    val TRANSPARENT = Color(0, 0, 0, 0)
}
