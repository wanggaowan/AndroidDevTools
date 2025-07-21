import org.jetbrains.kotlin.gradle.dsl.JvmTarget

repositories {
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

// 新版本的配置文件：https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    buildSearchableOptions = false
    // 是否开启增量构建
    instrumentCode = false

    pluginConfiguration {
        group = "com.wanggaowan.android.dev.tools"
        name = "AndroidDevTools"
        version = "2.4"

        ideaVersion {
            sinceBuild = "243"
            untilBuild = "10000.*"
        }
    }

    // publishing {
    //     // 用于发布插件的主机名,默认值https://plugins.jetbrains.com
    //     host = ""
    //     // 发布需要的秘钥
    //     token = "7hR4nD0mT0k3n_8f2eG"
    //     // 要将插件上传到的频道名称列表
    //     channels = listOf("default")
    //     // 指定是否应使用 IDE 服务插件存储库服务。
    //     ideServices = false
    //     // 发布插件更新并将其标记为隐藏，以防止在批准后公开可见。
    //     hidden = false
    // }

    // signing {
    //     cliPath = file("/path/to/marketplace-zip-signer-cli.jar")
    //     keyStore = file("/path/to/keyStore.ks")
    //     keyStorePassword = "..."
    //     keyStoreKeyAlias = "..."
    //     keyStoreType = "..."
    //     keyStoreProviderName = "..."
    //     privateKey = "..."
    //     privateKeyFile = file("/path/to/private.pem")
    //     password = "..."
    //     certificateChain = "..."
    //     certificateChainFile = file("/path/to/chain.crt")
    // }
}

val ideaProduct = "android-studio"
// val ideaProduct = "IC"
val androidPluginVersion = "243.23654.189"

dependencies {
    intellijPlatform {
        // androidStudio("2024.2.2", useInstaller = true)
        local("/Users/wgw/Documents/develop/project/ide plugin/test ide/Android Studio.app")

        val bundledPluginList = mutableListOf(
            "com.intellij.java",
            "org.jetbrains.kotlin",
        )
        if (ideaProduct == "android-studio") {
            bundledPluginList.add("org.jetbrains.android")
            bundledPluginList.add("com.android.tools.idea.smali")
        }

        val pluginList = mutableListOf<String>()
        if (ideaProduct == "IC") {
            pluginList.add("org.jetbrains.android:$androidPluginVersion")
        }

        bundledPlugins(bundledPluginList)
        plugins(pluginList)

        pluginVerifier()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}
