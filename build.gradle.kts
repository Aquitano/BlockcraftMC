group = "me.blockcraft"
version = "2.0.0-SNAPSHOT"

// LuckPerms only imports gzipped exports; the dev run tasks place this next to LuckPerms for `/lp import ranks`.
val packRanks by tasks.registering {
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
