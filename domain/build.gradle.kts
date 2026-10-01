plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        // domain はブラウザAPIに依存しないライブラリなので、テストはNode.jsで軽量に実行する
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // domain の公開API (Repository インターフェース等) が Flow を返すため、利用側にも公開する
            api(libs.kotlinx.coroutines.core)
            // domain の公開API (稼働記録の日付・月等) が LocalDate / YearMonth を使うため、利用側にも公開する
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
