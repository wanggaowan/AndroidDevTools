package com.wanggaowan.android.dev.tools.settings

import com.intellij.openapi.project.Project
import com.wanggaowan.android.dev.tools.utils.PropertiesSerializeUtils

/**
 * 插件设置
 *
 * @author Created by wanggaowan on 2023/2/19 16:38
 */
object PluginSettings {
    private const val EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG = "extractStr2L10nShowRenameDialog"
    // 阿里api配置
    private const val ALI_AK = "aliAk"
    private const val ALI_SK = "aliSk"

    private fun formatPath(path: String): String {
        var mapPath = path
        if (mapPath.startsWith("/")) {
            mapPath = mapPath.substring(1)
        }
        if (mapPath.endsWith("/")) {
            mapPath = mapPath.dropLast(1)
        }
        return mapPath
    }

    fun getExtractStr2L10nShowRenameDialog(project: Project? = null): Boolean {
        return getValue(project, EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG, "1") == "1"
    }

    fun setExtractStr2L10nShowRenameDialog(project: Project? = null, value: Boolean) {
        setValue(project, EXTRACT_STR_2_L10N_SHOW_RENAME_DIALOG, if (value) "1" else "0")
    }

    fun getAliAk(): String {
        return getValue(null, ALI_AK, "")
    }

    fun setAliAk(value: String) {
        setValue(null, ALI_AK, value)
    }

    fun getAliSk(): String {
        return getValue(null, ALI_SK,"")
    }

    fun setAliSk(value: String) {
        setValue(null, ALI_SK, value)
    }

    private fun getValue(project: Project?, key: String, defValue: String): String {
        return if (project == null) {
            var dir = PropertiesSerializeUtils.getString(key, defValue)
            if (dir.isEmpty()) {
                dir = defValue
            }
            dir
        } else {
            var dir = PropertiesSerializeUtils.getString(project, key)
            if (dir.isEmpty()) {
                dir = PropertiesSerializeUtils.getString(key, defValue)
                if (dir.isEmpty()) {
                    dir = defValue
                }
            }
            dir
        }
    }

    private fun setValue(project: Project? = null, key: String, value: String) {
        if (project == null) {
            PropertiesSerializeUtils.putString(key, value)
        } else {
            PropertiesSerializeUtils.putString(project, key, value)
        }
    }
}
