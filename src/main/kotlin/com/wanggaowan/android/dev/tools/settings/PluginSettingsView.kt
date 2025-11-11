package com.wanggaowan.android.dev.tools.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBInsets
import com.wanggaowan.android.dev.tools.ui.JLine
import com.wanggaowan.android.dev.tools.ui.UIColor
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 插件设置界面
 */
class PluginSettingsView {
    val panel: JPanel
    val extractStr2L10nShowRenameDialog = JBCheckBox("展示重命名弹窗")

    init {
        var builder = FormBuilder.createFormBuilder()
        builder = builder.addComponent(createCategoryTitle("提取多语言设置", marginTop = 10), 1)
        extractStr2L10nShowRenameDialog.border = BorderFactory.createEmptyBorder(4, 10, 0, 0)
        builder = builder.addComponent(extractStr2L10nShowRenameDialog, 1)

        panel = builder.addComponentFillVertically(JPanel(), 0).panel
    }

    private fun createCategoryTitle(title: String, marginTop: Int? = null, marginLeft: Int? = null): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val jLabel = JLabel(title)
        panel.add(jLabel, BorderLayout.WEST)

        val divider = JLine(UIColor.LINE_COLOR, JBInsets(0, 10, 0, 0))
        panel.add(divider, BorderLayout.CENTER)

        if (marginTop != null || marginLeft != null) {
            panel.border = BorderFactory.createEmptyBorder(marginTop ?: 0, marginLeft ?: 0, 0, 0)
        }
        return panel
    }
}
