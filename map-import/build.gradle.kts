import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("blockcraft.java-conventions")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.adventure.nbt)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// One import at a time works in run/map-import: the copied old world, the finishing server and the finished template.
// A dry run and the real run of the same source reuse the finished world (worlds/README.md, "Importing an old 1.8 map").
val scratch = rootProject.layout.projectDirectory.dir("run/map-import")
val cli = "me.blockcraft.mapimport.MapImport"

fun property(name: String) = providers.gradleProperty(name).orNull

val prepareMapImport = tasks.register<JavaExec>("prepareMapImport") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = cli
    argumentProviders.add(CommandLineArgumentProvider {
        val source = property("source") ?: throw GradleException("Name the old world with -Psource=<directory>")
        listOf("prepare", "--scratch", scratch.asFile.path, "--source", rootProject.file(source).path)
    })
}

val finishMapImport = tasks.register<RunServer>("finishMapImport") {
    dependsOn(prepareMapImport)
    onlyIf("the copied world is not finished yet") { !scratch.file("finished").asFile.exists() }
    minecraftVersion(libs.versions.minecraft.get())
    runDirectory = scratch.dir("server")
    pluginJars.from(tasks.jar)
    systemProperty("com.mojang.eula.agree", "true")
    systemProperty("paper.disableMigrationDelay", "true")
    systemProperty("blockcraft.mapimport.scratch", scratch.asFile.path)
    doLast {
        if (!scratch.file("finished").asFile.exists()) {
            throw GradleException("The finishing server stopped before every chunk was finished; see its log above")
        }
    }
}

tasks.register<JavaExec>("importMap") {
    group = "blockcraft"
    description = "Turns an old Minecraft world into a map template: -Psource, -Ptarget, optional -Pmode, -Pbounds, -Pmargin, -Playout, -PdryRun"
    dependsOn(finishMapImport)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = cli
    argumentProviders.add(CommandLineArgumentProvider {
        buildList {
            addAll(listOf("write", "--scratch", scratch.asFile.path))
            property("target")?.let { addAll(listOf("--target", rootProject.file(it).path)) }
            listOf("mode", "bounds", "margin", "layout").forEach { name ->
                property(name)?.let { addAll(listOf("--$name", it)) }
            }
            if (providers.gradleProperty("dryRun").isPresent) {
                add("--dry-run")
            }
        }
    })
}
