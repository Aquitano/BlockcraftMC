plugins {
    id("blockcraft.java-conventions")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("paper-api").get())
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
}

// shadowJar is the deployable plugin jar; the plain jar only feeds sibling compile classpaths.
tasks.jar {
    archiveClassifier = "slim"
}

tasks.shadowJar {
    archiveClassifier = ""
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion(libs.findVersion("minecraft").get().requiredVersion)
    systemProperty("com.mojang.eula.agree", "true")
}
