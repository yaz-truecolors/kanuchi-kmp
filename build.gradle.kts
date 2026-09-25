import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// CI（.github/workflows/ci.yml の build-lint-test ジョブ）と同じ検証を1コマンドで実行する集約タスク。
// 各プロジェクトの `check` が ktlintCheck / detekt / allTests を含むため、それに本番ビルドを加えるだけにして重複定義を避ける。
tasks.register("verify") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "CIと同じ検証（ktlintCheck / detekt / allTests / wasmJsBrowserDistribution）を実行する。"
    dependsOn(tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME))
    dependsOn(subprojects.map { "${it.path}:${LifecycleBasePlugin.CHECK_TASK_NAME}" })
    dependsOn(":app-wasmjs:wasmJsBrowserDistribution")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        filter {
            // Compose リソースジェネレータ等の生成コードは lint 対象外にする
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            if (this is org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
