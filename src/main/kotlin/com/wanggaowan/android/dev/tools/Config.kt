package com.wanggaowan.android.dev.tools

/**
 * 全局配置
 *
 * @author Created by wanggaowan on 2025/7/21 13:31
 */
class Config {
    /*
      线程说明文档：https://plugins.jetbrains.com/docs/intellij/threading-model.html
      无需索引准备完毕即可执行的action（DumbAwareAction）说明：https://plugins.jetbrains.com/docs/intellij/indexing-and-psi-stubs.html
     */
    companion object {
        // 开发模式
        const val DEV_MODE = false
    }
}
