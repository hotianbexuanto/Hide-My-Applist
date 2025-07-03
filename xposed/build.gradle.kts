import com.android.ide.common.signing.KeystoreHelper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.PrintStream
import java.util.Locale

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.refine)
    alias(libs.plugins.kotlin)
}

android {
    namespace = "icu.nullptr.hidemyapplist.xposed"

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(21)
}

afterEvaluate {
    //noinspection WrongGradleMethod
    android.libraryVariants.forEach { variant ->
        val variantCapped = variant.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val variantLowered = variant.name.lowercase(Locale.ROOT)

        val outSrcDir = layout.buildDirectory.dir("generated/source/signInfo/${variantLowered}")
        val outSrc = outSrcDir.get().file("icu/nullptr/hidemyapplist/Magic.java")
        val signInfoTask = tasks.register("generate${variantCapped}SignInfo") {
            outputs.file(outSrc)
            doLast {
                // 注意：签名验证已禁用（修改日期：2025-07-03）
                // 生成空的Magic类以避免构建错误
                // 原始逻辑已注释，如需恢复请取消注释

                outSrc.asFile.parentFile.mkdirs()
                PrintStream(outSrc.asFile).apply {
                    println("package icu.nullptr.hidemyapplist;")
                    println("// 注意：签名验证已禁用，此类仅为兼容性保留")
                    println("public final class Magic {")
                    println("    // 空的魔术数字数组 - 签名验证已禁用")
                    println("    public static final byte[] magicNumbers = {};")
                    println("}")
                }

                /*
                // === 原始签名信息生成逻辑（已禁用）===
                val sign = android.buildTypes[variantLowered].signingConfig
                val certificateInfo = KeystoreHelper.getCertificateInfo(
                    sign?.storeType,
                    sign?.storeFile,
                    sign?.storePassword,
                    sign?.keyPassword,
                    sign?.keyAlias
                )
                PrintStream(outSrc.asFile).apply {
                    println("package icu.nullptr.hidemyapplist;")
                    println("public final class Magic {")
                    print("public static final byte[] magicNumbers = {")
                    val bytes = certificateInfo.certificate.encoded
                    print(bytes.joinToString(",") { it.toString() })
                    println("};")
                    println("}")
                }
                // === 原始签名信息生成逻辑结束 ===
                */
            }
        }
        variant.registerJavaGeneratingTask(signInfoTask, outSrcDir.get().asFile)

        val kotlinCompileTask = tasks.findByName("compile${variantCapped}Kotlin") as KotlinCompile
        kotlinCompileTask.dependsOn(signInfoTask)
        val srcSet = objects.sourceDirectorySet("magic", "magic").srcDir(outSrcDir)
        kotlinCompileTask.source(srcSet)
    }
}

dependencies {
    implementation(projects.common)

    implementation(libs.androidx.annotation.jvm)
    implementation(libs.com.android.tools.build.apksig)
    implementation(libs.com.github.kyuubiran.ezxhelper)
    implementation(libs.dev.rikka.hidden.compat)
    compileOnly(libs.de.robv.android.xposed.api)
    compileOnly(libs.dev.rikka.hidden.stub)
}
