plugins {
    id("xyz.jpenilla.run-velocity")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Runs one Velocity proxy (port 25565) with every plugin here plus LuckPerms; backends register themselves.
// `-Pplugins=system` selects the network plugin, including reports.
val selectedPlugins = providers.gradleProperty("plugins").map { it.split(",").map(String::trim).toSet() }

tasks.runVelocity {
    velocityVersion(libs.findVersion("velocity-api").get().requiredVersion)
    runDirectory = rootProject.layout.projectDirectory.dir("run/velocity")
    dependsOn(":packRanks")
    doFirst {
        copy {
            from(rootProject.file("dev/velocity"))
            into(runDirectory)
        }
        copy {
            from(rootProject.layout.buildDirectory.file("luckperms/ranks.json.gz"))
            into(runDirectory.dir("plugins/luckperms"))
        }
    }
    pluginJars.from(provider {
        subprojects
            .filter { !selectedPlugins.isPresent || it.name in selectedPlugins.get() }
            .map { it.tasks.named("shadowJar") }
    })
    downloadPlugins {
        url("https://download.luckperms.net/1669/velocity/LuckPerms-Velocity-5.5.82.jar")
    }
}
