plugins {
    id("blockcraft.java-conventions")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-velocity")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("velocity-api").get())
    annotationProcessor(libs.findLibrary("velocity-api").get())
}

tasks.jar {
    archiveClassifier = "slim"
}

tasks.shadowJar {
    archiveClassifier = ""
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.runVelocity {
    velocityVersion(libs.findVersion("velocity-api").get().requiredVersion)
}

// @Plugin(version = ...) needs a compile-time constant, so the Gradle version is generated into a class.
val generateBuildConstants by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/buildConstants/java/main")
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("me/blockcraft/build/BuildConstants.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package me.blockcraft.build;

            public final class BuildConstants {

                public static final String VERSION = "$version";

                private BuildConstants() {
                }
            }
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.main {
    java.srcDir(generateBuildConstants)
}
