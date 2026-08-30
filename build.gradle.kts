plugins {
    // no org.jetbrains.kotlin.android: AGP 9+ has built-in Kotlin support, that plugin is incompatible now
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        // parallel disabled: caused intermittent CI failures on constrained runners
        buildUponDefaultConfig = true
        allRules = false
        parallel = false
        config.setFrom("$rootDir/config/detekt/detekt.yml")
    }
}
