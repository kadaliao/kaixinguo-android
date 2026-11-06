plugins {
    alias(libs.plugins.android.application) apply false
    // AGP builds Kotlin itself; naming the plugin here picks the Kotlin
    // version, which the Compose compiler must match.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
