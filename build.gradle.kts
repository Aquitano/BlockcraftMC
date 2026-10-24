group = "me.blockcraft"
version = "2.0.0-SNAPSHOT" + providers.environmentVariable("BLOCKCRAFT_BUILD").map { "+$it" }.getOrElse("")

// LuckPerms only imports gzipped exports; the dev run tasks place this next to LuckPerms for `/lp import ranks`.
val packRanks = tasks.register("packRanks") {
    val source = layout.projectDirectory.file("luckperms/ranks.json")
    val target = layout.buildDirectory.file("luckperms/ranks.json.gz")
    inputs.file(source)
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        java.util.zip.GZIPOutputStream(file.outputStream()).use { source.asFile.inputStream().copyTo(it) }
    }
}

// Plugins we do not build are staged like our own jars, so no server start depends on a download. Each lists where
// its releases live, then any mirror of the same release that steps in when the first cannot be reached; LuckPerms
// publishes only on its own site.
fun downloadPlugin(name: String, vararg sources: String) = tasks.register("download$name") {
    val target = layout.buildDirectory.file("plugins/${sources.first().substringAfterLast('/')}")
    inputs.property("sources", sources.toList())
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        val failures = mutableListOf<String>()
        for (source in sources) {
            try {
                java.net.URI(source).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                return@doLast
            } catch (e: java.io.IOException) {
                failures += "$source: $e"
            }
        }
        throw GradleException("Could not download $name:\n" + failures.joinToString("\n"))
    }
}

val viaVersion = libs.versions.viaversion.get()
val downloadViaVersion = downloadPlugin(
    "ViaVersion",
    "https://github.com/ViaVersion/ViaVersion/releases/download/$viaVersion/ViaVersion-$viaVersion.jar",
    "https://api.modrinth.com/maven/maven/modrinth/viaversion/$viaVersion/viaversion-$viaVersion.jar",
)
val downloadLuckPermsPaper = downloadPlugin(
    "LuckPermsPaper",
    "https://download.luckperms.net/1669/bukkit/loader/LuckPerms-Bukkit-5.5.82.jar",
)
val downloadLuckPermsVelocity = downloadPlugin(
    "LuckPermsVelocity",
    "https://download.luckperms.net/1669/velocity/LuckPerms-Velocity-5.5.82.jar",
)

// Lays out build/docker for docker-compose.yml: build/docker/paper/<module>/<module>.jar (one directory per plugin,
// so a compose service lists the ones it runs, LuckPerms and ViaVersion among them), build/docker/velocity/<module>.jar
// with LuckPerms, ranks.json.gz next to LuckPerms on both sides, and the resource pack in the proxy plugin's data
// directory.
tasks.register<Sync>("stagePlugins") {
    project(":paper").subprojects.forEach { plugin ->
        from(plugin.tasks.named("shadowJar")) {
            into("paper/${plugin.name}")
            rename { "${plugin.name}.jar" }
        }
    }
    project(":velocity").subprojects.forEach { plugin ->
        from(plugin.tasks.named("shadowJar")) {
            into("velocity")
            rename { "${plugin.name}.jar" }
        }
    }
    from(downloadViaVersion) {
        into("paper/viaversion")
        rename { "ViaVersion.jar" }
    }
    from(downloadLuckPermsPaper) {
        into("paper/luckperms")
        rename { "LuckPerms.jar" }
    }
    from(downloadLuckPermsVelocity) {
        into("velocity")
        rename { "LuckPerms.jar" }
    }
    from(packRanks) {
        into("paper/luckperms/LuckPerms")
    }
    from(packRanks) {
        into("velocity/luckperms")
    }
    from(project(":resourcepack").tasks.named("pack")) {
        into("velocity/blockcraft-system")
    }
    into(layout.buildDirectory.dir("docker"))
}
