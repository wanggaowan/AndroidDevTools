package com.wanggaowan.android.dev.tools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.wanggaowan.android.dev.tools.utils.ex.isAndroidProject
import javax.swing.JComponent

/**
 * 项目插件设置界面配置
 *
 * @author Created by wanggaowan on 2023/3/5 16:17
 */
class ProjectPluginSettingsConfigurable(val project: Project) : Configurable {
    private var mSettingsView: PluginSettingsView? = null

    override fun getDisplayName(): String {
        return "AndroidDevTools"
    }

    override fun createComponent(): JComponent {
        mSettingsView = PluginSettingsView()
        return mSettingsView!!.panel
    }

    override fun isModified(): Boolean {
        if (isExtractStr2L10nModified()) {
            return true
        }

        return false
    }

    private fun isExtractStr2L10nModified(): Boolean {
        if (PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper()) != mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected) {
            return true
        }

        if (PluginSettings.getExtractStr2L10nTranslateOther(getProjectWrapper()) != mSettingsView?.extractStr2L10nTranslateOther?.isSelected) {
            return true
        }

        return false
    }

    override fun apply() {
        applyExtractStr2L10n()
    }

    private fun applyExtractStr2L10n() {
        PluginSettings.setExtractStr2L10nShowRenameDialog(
            getProjectWrapper(),
            mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected ?: true
        )
        PluginSettings.setExtractStr2L10nTranslateOther(
            getProjectWrapper(),
            mSettingsView?.extractStr2L10nTranslateOther?.isSelected ?: true
        )
    }

    override fun reset() {
        resetExtractStr2L10n()
    }

    private fun resetExtractStr2L10n() {
        mSettingsView?.extractStr2L10nShowRenameDialog?.isSelected =
            PluginSettings.getExtractStr2L10nShowRenameDialog(getProjectWrapper())
        mSettingsView?.extractStr2L10nTranslateOther?.isSelected =
            PluginSettings.getExtractStr2L10nTranslateOther(getProjectWrapper())
    }

    override fun disposeUIResources() {
        mSettingsView = null
    }

    private fun getProjectWrapper(): Project? {
        if (project.isDefault || !project.isAndroidProject) {
            return null
        }
        return project
    }
}
