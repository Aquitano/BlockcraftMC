plugins {
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Runs one Paper server (port 25566, behind the dev Velocity) with every plugin in this directory plus LuckPerms.
// Needs the MariaDB from docker-compose.yml. `-Pplugins=core,lobby` limits the set (BlockcraftAPI is always included).
val selectedPlugins = providers.gradleProperty("plugins").map { it.split(",").map(String::trim).toSet() }

tasks.runServer {
    minecraftVersion(libs.findVersion("minecraft").get().requiredVersion)
    runDirectory = rootProject.layout.projectDirectory.dir("run/paper")
    systemProperty("com.mojang.eula.agree", "true")
    doFirst {
        copy {
            from(rootProject.file("dev/paper"))
            into(runDirectory)
        }
    }
    pluginJars.from(provider {
        subprojects
            .filter { it.name == "api" || !selectedPlugins.isPresent || it.name in selectedPlugins.get() }
            .map { it.tasks.named("shadowJar") }
    })
    downloadPlugins {
        url("https://download.luckperms.net/1669/bukkit/loader/LuckPerms-Bukkit-5.5.82.jar")
    }
}
