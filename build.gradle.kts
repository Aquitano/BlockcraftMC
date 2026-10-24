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

// ViaVersion is staged like our own jars, so no server start depends on a download. GitHub is where its releases
// live; Modrinth carries the same release and steps in when GitHub cannot be reached.
val downloadViaVersion = tasks.register("downloadViaVersion") {
    val version = libs.versions.viaversion.get()
    val sources = listOf(
        "https://github.com/ViaVersion/ViaVersion/releases/download/$version/ViaVersion-$version.jar",
        "https://api.modrinth.com/maven/maven/modrinth/viaversion/$version/viaversion-$version.jar",
    )
    val target = layout.buildDirectory.file("viaversion/ViaVersion-$version.jar")
    inputs.property("sources", sources)
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
        throw GradleException("Could not download ViaVersion $version:\n" + failures.joinToString("\n"))
    }
}

// Lays out build/docker for docker-compose.yml: build/docker/paper/<module>/<module>.jar (one directory per plugin,
// so a compose service lists the ones it runs) next to paper/viaversion, build/docker/velocity/<module>.jar, ranks.json.gz next to
// LuckPerms on both sides, and the resource pack in the proxy plugin's data directory.
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
