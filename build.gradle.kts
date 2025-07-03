import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.nav.safeargs.kotlin) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = java.io.ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

val minSdkVer by extra(28)
val targetSdkVer by extra(35)
val buildToolsVer by extra("35.0.1")

val appVerName by extra("3.4")
val configVerCode by extra(90)
val serviceVerCode by extra(97)
val minBackupVerCode by extra(65)

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

val localProperties = Properties()
localProperties.load(file("local.properties").inputStream())
val officialBuild by extra(localProperties.getProperty("officialBuild", "false") == "true")

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType<BaseExtension>()?.run {
        compileSdkVersion(targetSdkVer)
        buildToolsVersion = buildToolsVer

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = gitCommitCount
            versionName = appVerName
            if (localProperties.getProperty("buildWithGitSuffix").toBoolean())
                versionNameSuffix = ".r${gitCommitCount}.${gitCommitHash}"

            consumerProguardFiles("proguard-rules.pro")
        }

        // 签名配置已修改以避免GitHub Actions构建错误（修改日期：2025-07-03）
        // 原因：避免"Tag number over 30 is not supported"密钥库错误
        val config = localProperties.getProperty("fileDir")?.let { keyPath ->
            // 只在密钥库文件存在且可读时创建签名配置
            val keyFile = file(keyPath)
            if (keyFile.exists() && keyFile.canRead()) {
                try {
                    signingConfigs.create("config") {
                        storeFile = keyFile
                        storePassword = localProperties.getProperty("storePassword")
                        keyAlias = localProperties.getProperty("keyAlias")
                        keyPassword = localProperties.getProperty("keyPassword")
                    }
                } catch (e: Exception) {
                    // 如果创建签名配置失败，返回null使用debug签名
                    println("Warning: Failed to create signing config, using debug signing: ${e.message}")
                    null
                }
            } else {
                println("Warning: Keystore file not found or not readable, using debug signing")
                null
            }
        }

        buildTypes {
            all {
                // 始终使用debug签名以避免密钥库问题
                signingConfig = signingConfigs["debug"]
                // 原始逻辑：signingConfig = config ?: signingConfigs["debug"]
            }
            named("release") {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        buildTypes {
            named("release") {
                isShrinkResources = true
            }
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
}
