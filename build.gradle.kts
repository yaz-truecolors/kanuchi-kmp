import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin

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

    // Kotlin Gradle プラグインがダウンロードする Node.js（wasmJs の Node テストと :app-wasmjs:smokeTest で使う）のバージョンを固定する。
    // バージョンは gradle/libs.versions.toml の nodejs で管理する（Renovate の customManagers で更新を検知する）。
    plugins.withType<WasmNodeJsPlugin> {
        the<WasmNodeJsEnvSpec>().version.set(libs.versions.nodejs)
    }
}

// CI（.github/workflows/ci.yml の build-lint-test ジョブ）と同じ検証を1コマンドで実行する集約タスク。
// 各プロジェクトの `check` が ktlintCheck / detekt / allTests を含むため、それに本番ビルドと、本番ビルドを実ブラウザで開く
// スモークテスト（:app-wasmjs:smokeTest。wasmJsBrowserDistribution に依存する）を加えるだけにして重複定義を避ける。
tasks.register("verify") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "CIと同じ検証（ktlintCheck / detekt / allTests / wasmJsBrowserDistribution / smokeTest）を実行する。"
    dependsOn(tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME))
    dependsOn(subprojects.map { "${it.path}:${LifecycleBasePlugin.CHECK_TASK_NAME}" })
    dependsOn(":app-wasmjs:wasmJsBrowserDistribution")
    dependsOn(":app-wasmjs:smokeTest")
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
        // detekt の既定 source は src/main/kotlin 等の JVM レイアウトで、KMP の src/commonMain/kotlin 等を拾わず
        // `detekt` が NO-SOURCE になる。src/<ソースセット名>/kotlin を丸ごと対象にし、ソースセット追加にも追従させる。
        // ルートを src にしているため build/ 配下の生成コード（Compose Resources の Res.kt 等）は含まれない。
        source.setFrom(
            fileTree("src") {
                include("*/kotlin/**/*.kt", "*/kotlin/**/*.kts")
            },
        )
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        // build/ 配下の生成コード（Compose Resources の Res.kt 等）は常に解析対象外にする。
        // detektWasmJsMain 等のソースセット別タスクは生成ディレクトリ自体を source ルートに持つため、
        // "**/build/**" のような相対パターンでは除外できない。絶対パスで判定する。
        val moduleBuildDir = layout.buildDirectory.get().asFile
        exclude { it.file.startsWith(moduleBuildDir) }
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
