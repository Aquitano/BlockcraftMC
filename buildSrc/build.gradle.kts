plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.error.prone.gradle)
    implementation(libs.run.task)
    implementation(libs.shadow)
}
