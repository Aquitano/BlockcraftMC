plugins {
    id("blockcraft.java-conventions")
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // Provided at runtime by Paper (plugin.yml libraries) and shaded into Velocity plugins.
    compileOnly(libs.findLibrary("hikaricp").get())
    // Both Paper and Velocity ship these on the plugin classpath.
    compileOnly(libs.findLibrary("adventure-minimessage").get())
    compileOnly(libs.findLibrary("snakeyaml").get())
    compileOnly(libs.findLibrary("slf4j-api").get())
}
