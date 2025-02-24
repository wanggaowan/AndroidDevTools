package com.wanggaowan.android.dev.tools.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import javax.swing.JComponent

/**
 * 吐司工具,吐司显示在指定组件上面，附带一个指示箭头
 *
 * @author Created by wanggaowan on 2022/5/8 17:13
 */
object Toast {

    /**
     * 弹出吐司
     * @param jComponent 指定弹出吐司依附的组件，显示在此组件中间
     * @param type 消息类型
     * @param msg 消息内容
     */
    @JvmStatic
    fun show(jComponent: JComponent, type: MessageType, msg: String) {
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(msg, type, null)
            .setFadeoutTime(7500)
            .createBalloon()
            .show(RelativePoint.getCenterOf(jComponent), Balloon.Position.above)
    }

    /**
     * 弹出吐司
     * @param project 指定弹出吐司依附的项目，显示在项目窗口中间
     * @param type 消息类型
     * @param msg 消息内容
     */
    @JvmStatic
    fun show(project: Project, type: MessageType, msg: String) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val component = statusBar.component
        if (component == null) {
            return
        }

        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(msg, type, null)
            .setFadeoutTime(7500)
            .createBalloon()
            .show(RelativePoint.getCenterOf(component), Balloon.Position.atRight)
    }
}
