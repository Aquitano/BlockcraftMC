plugins {
    id("xyz.jpenilla.run-paper")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Runs one Paper server on port 25566 with LuckPerms and ViaVersion. Needs the dev MariaDB and Velocity.
// `-Pgame=zombies` selects its group, map and isolated run directory; the default remains the lobby in run/paper.
// `-Pplugins=core,lobby` limits the plugin set (BlockcraftAPI is always included).
val selectedPlugins = providers.gradleProperty("plugins").map { it.split(",").map(String::trim).toSet() }
val localGame = providers.gradleProperty("game").orElse("lobby")

tasks.runServer {
    minecraftVersion(libs.findVersion("minecraft").get().requiredVersion)
    runDirectory = rootProject.layout.projectDirectory.dir(if (providers.gradleProperty("game").isPresent) "run/paper-${localGame.get()}" else "run/paper")
    systemProperty("com.mojang.eula.agree", "true")
    environment("BLOCKCRAFT_SERVER_NAME", localGame.get())
    if (providers.gradleProperty("game").isPresent) {
        environment("BLOCKCRAFT_SERVER_GROUP", localGame.get())
    }
    environment("BLOCKCRAFT_WORLD_TEMPLATE", rootProject.file("worlds/${localGame.get()}").absolutePath)
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
    pluginJars.from(rootProject.tasks.named("downloadLuckPermsPaper"), rootProject.tasks.named("downloadViaVersion"))
}
