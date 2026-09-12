plugins {
    id("xyz.jpenilla.run-velocity")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Runs one Velocity proxy (port 25565, forwarding to the dev Paper on 25566) with every plugin here plus LuckPerms.
// `-Pplugins=report` limits the set.
val selectedPlugins = providers.gradleProperty("plugins").map { it.split(",").map(String::trim).toSet() }

tasks.runVelocity {
    velocityVersion(libs.findVersion("velocity-api").get().requiredVersion)
    runDirectory = rootProject.layout.projectDirectory.dir("run/velocity")
    doFirst {
        copy {
            from(rootProject.file("dev/velocity"))
            into(runDirectory)
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
