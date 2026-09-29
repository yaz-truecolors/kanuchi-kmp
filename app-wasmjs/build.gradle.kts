import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("kanuchi")
        browser {
            commonWebpackConfig {
                outputFileName = "kanuchi.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":data"))
            implementation(project(":presentation"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.browser)
        }
    }
}

// ---- UI 描画スモークテスト（e2e/）----
// 本番ビルド（wasmJsBrowserDistribution の成果物）をヘッドレス Chromium で開き、起動して canvas に描画されるかだけを確認する。
// Node.js は Kotlin Gradle プラグインがダウンロードするもの（kotlinWasmNodeJsSetup）を使い、ブラウザは Playwright 同梱の
// Chromium を使うため、開発端末に Node.js や Chrome を入れていなくても実行できる。
val e2eDir = rootProject.layout.projectDirectory.dir("e2e")
val nodeExecutable = the<WasmNodeJsEnvSpec>().executable
val playwrightCli = e2eDir.file("node_modules/@playwright/test/cli.js").asFile.path

// Node.js 配布物に同梱の npm を node 経由で直接起動する（PATH に node が無くても動かすため）
fun npmCliPath(node: String): String {
    val nodeDir = File(node).parentFile
    return listOf(
        nodeDir.resolve("../lib/node_modules/npm/bin/npm-cli.js"), // macOS / Linux
        nodeDir.resolve("node_modules/npm/bin/npm-cli.js"), // Windows
    ).first { it.isFile }.canonicalPath
}

val smokeTestNpmCi by tasks.registering(Exec::class) {
    description = "スモークテストの npm 依存を package-lock.json どおりにインストールする（npm ci）。"
    dependsOn("kotlinWasmNodeJsSetup")
    workingDir(e2eDir)
    executable(nodeExecutable.get())
    argumentProviders.add(CommandLineArgumentProvider { listOf(npmCliPath(nodeExecutable.get()), "ci", "--no-audit", "--no-fund") })
    inputs.files(e2eDir.file("package.json"), e2eDir.file("package-lock.json"))
    outputs.dir(e2eDir.dir("node_modules"))
}

val smokeTestInstallBrowser by tasks.registering(Exec::class) {
    description = "スモークテスト用の Chromium（Playwright 同梱版）をダウンロードする。導入済みなら何もしない。"
    dependsOn(smokeTestNpmCi)
    workingDir(e2eDir)
    executable(nodeExecutable.get())
    args(playwrightCli, "install", "--only-shell", "chromium")
}

val productionDistDir =
    layout.buildDirectory
        .dir("dist/wasmJs/productionExecutable")
        .get()
        .asFile.path

// 本番ビルドをキャッシュ無効（Cache-Control: no-store）で静的配信する。開発サーバー（wasmJsBrowserDevelopmentRun）は
// キャッシュやライブリロードの都合で変更が反映されないことがあるため、確実に最新の成果物を確認したいときに使う。
// 配信にはスモークテストと同じ e2e/serve.js（Node.js 標準モジュールのみ）を使う。停止するまで終了しない。
tasks.register<Exec>("serveDistribution") {
    group = "application"
    description = "本番ビルドを http://127.0.0.1:<SERVE_PORT（既定 8081）>/ で静的配信する（キャッシュ無効）。"
    dependsOn("wasmJsBrowserDistribution", "kotlinWasmNodeJsSetup")
    workingDir(e2eDir)
    executable(nodeExecutable.get())
    args("serve.js")
    environment("DIST_DIR", productionDistDir)
    environment("PORT", providers.environmentVariable("SERVE_PORT").getOrElse("8081"))
}

tasks.register<Exec>("smokeTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "本番ビルドをヘッドレス Chromium で開き、実行時エラーなく canvas に描画されるかを確認する。"
    dependsOn(smokeTestInstallBrowser)
    val distribution = tasks.named("wasmJsBrowserDistribution")
    inputs.files(distribution).withPropertyName("distribution")
    inputs
        .files(
            e2eDir.file("package-lock.json"),
            e2eDir.file("playwright.config.js"),
            e2eDir.file("serve.js"),
            e2eDir.dir("tests"),
        ).withPropertyName("e2eSources")
    outputs.dir(e2eDir.dir("test-results"))
    workingDir(e2eDir)
    executable(nodeExecutable.get())
    args(playwrightCli, "test")
    environment("DIST_DIR", productionDistDir)
}
