package com.wanggaowan.android.dev.tools.utils

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

/**
 * 属性序列化工具
 *
 * @author Created by wanggaowan on 2022/9/20 13:22
 */
object PropertiesSerializeUtils {

    // <editor-fold desc="基于IDE程序级别的序列化，只要IDE不卸载，序列化一致存在">
    @JvmOverloads
    fun getBoolean(name: String, defaultValue: Boolean = false):Boolean {
        return PropertiesComponent.getInstance().getBoolean(name, defaultValue)
    }

    fun setBoolean(name: String, value: Boolean) {
        PropertiesComponent.getInstance().setValue(name, value)
    }
    // </editor-fold>

    // <editor-fold desc="基于项目级别的序列化，项目配置文件删除，序列化即删除">
    @JvmOverloads
    fun getBoolean(project: Project, name: String, defaultValue: Boolean = false):Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(name, defaultValue)
    }

    fun setBoolean(project: Project, name: String, value: Boolean) {
        PropertiesComponent.getInstance(project).setValue(name, value)
    }
    // </editor-fold>
}
