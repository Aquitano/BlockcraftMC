plugins {
    id("blockcraft.java-conventions")
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    testRuntimeOnly(libs.findLibrary("mariadb-client").get())
    // Shaded by the Paper and Velocity runtime plugins.
    compileOnly(libs.findLibrary("hikaricp").get())
    // Both Paper and Velocity ship these on the plugin classpath.
    compileOnly(libs.findLibrary("adventure-minimessage").get())
    compileOnly(libs.findLibrary("snakeyaml").get())
    compileOnly(libs.findLibrary("slf4j-api").get())
}

// MessageKeysTest reads every module's sources and bundled messages, so they are inputs of this module's tests.
tasks.test {
    inputs.files(rootProject.fileTree("paper") { include("*/src/main/**") }, rootProject.fileTree("velocity") { include("*/src/main/**") })
}
