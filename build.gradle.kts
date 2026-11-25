// Root build file. Plugin versions are declared here with `apply false`
// so that all modules share a single source of truth (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
