pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Gradle デーモンを JDK 17 で起動するための JDK 自動取得（gradle/gradle-daemon-jvm.properties の toolchainUrl.* の生成元）。
// ./gradlew を実行する JDK（JAVA_HOME / PATH の java）が 17 以外でも、デーモンは JDK 17 で動く（見つからなければ自動取得する）。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "kanuchi"

include(
    ":domain",
    ":data",
    ":presentation",
    ":app-wasmjs",
)
