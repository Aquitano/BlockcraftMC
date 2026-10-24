plugins {
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Runs one Paper server (port 25566, announced to the dev Velocity as "lobby") with every plugin in this directory
// plus LuckPerms and ViaVersion, on the same lobby map as the compose network. Needs the MariaDB from docker-compose.yml.
// `-Pplugins=core,lobby` limits the set (BlockcraftAPI is always included).
val selectedPlugins = providers.gradleProperty("plugins").map { it.split(",").map(String::trim).toSet() }

tasks.runServer {
    minecraftVersion(libs.findVersion("minecraft").get().requiredVersion)
    runDirectory = rootProject.layout.projectDirectory.dir("run/paper")
    systemProperty("com.mojang.eula.agree", "true")
    environment("BLOCKCRAFT_SERVER_NAME", "lobby")
    environment("BLOCKCRAFT_WORLD_TEMPLATE", rootProject.file("worlds/lobby").absolutePath)
    dependsOn(":packRanks")
    doFirst {
        copy {
            from(rootProject.file("dev/paper"))
            into(runDirectory)
        }
        copy {
            from(rootProject.layout.buildDirectory.file("luckperms/ranks.json.gz"))
            into(runDirectory.dir("plugins/LuckPerms"))
        }
    }
    pluginJars.from(provider {
        subprojects
            .filter { it.name == "api" || !selectedPlugins.isPresent || it.name in selectedPlugins.get() }
            .map { it.tasks.named("shadowJar") }
    })
    pluginJars.from(rootProject.tasks.named("downloadViaVersion"))
    downloadPlugins {
        url("https://download.luckperms.net/1669/bukkit/loader/LuckPerms-Bukkit-5.5.82.jar")
    }
}
