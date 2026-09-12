group = "me.blockcraft"
version = "2.0.0-SNAPSHOT"

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

// Lays out build/docker for docker-compose.yml: build/docker/paper/<module>/<module>.jar (one directory per plugin,
// so a compose service lists the ones it runs), build/docker/velocity/<module>.jar, and ranks.json.gz next to
// LuckPerms on both sides.
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
    from(packRanks) {
        into("paper/luckperms/LuckPerms")
    }
    from(packRanks) {
        into("velocity/luckperms")
    }
    into(layout.buildDirectory.dir("docker"))
}
