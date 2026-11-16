plugins {
    base
}

fun assetCommand(vararg arguments: String) = listOf("uv", "run", "--locked", "python") + arguments

fun generateAssets(taskName: String, family: String) = tasks.register<Exec>(taskName) {
    group = "assets"
    description = "Generates and validates $family in isolation, then updates accepted assets."
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("assets.py", "generate", family, "--apply"))
}

generateAssets("generateTextures", "ui")
generateAssets("generateSounds", "sounds")
generateAssets("generateSternenfrontSounds", "sternenfront-sounds")
generateAssets("generateStorySounds", "story-sounds")
generateAssets("generateProps", "props")
generateAssets("generateSternenfrontArt", "bodies")
generateAssets("generateRigModels", "rigs")
generateAssets("generateSternenfrontGallery", "gallery")
generateAssets("generateYardArt", "yard")
generateAssets("generateObjectiveArt", "objectives")
generateAssets("generatePlanetKitArt", "planet")
generateAssets("generateMilitaryKitArt", "shared")
generateAssets("generateKitActors", "actors")
generateAssets("generateHud", "hud")
generateAssets("generateStationArt", "stations")
generateAssets("generateBattle", "battle")
generateAssets("generateStoryArt", "story")
generateAssets("generatePresentation", "presentation")

tasks.register<Exec>("regenerate") {
    group = "assets"
    description = "Generates every authored visual family in isolation, validates it, and updates accepted output. Finished imports are preserved."
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("assets.py", "generate", "all", "--apply"))
}

val verifyAssets = tasks.register<Exec>("verifyAssets") {
    group = "verification"
    description = "Validates accepted native assets without running generators."
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("assets.py", "check"))
}

// Players get credits for what the pack contains; the map provenance and open questions stay in the repository.
val packCredits = setOf("Music", "Sound effects", "Models and textures")
val packSource = providers.gradleProperty("packSource").map { file(it) }.getOrElse(file("src"))

val verifySplitPack = tasks.register<Exec>("verifySplitPack") {
    workingDir(layout.projectDirectory)
    commandLine("python3", "split_pack.py", "--source", packSource.absolutePath, "--check")
}

val testSplitPack = tasks.register<Exec>("testSplitPack") {
    workingDir(layout.projectDirectory)
    commandLine("python3", "-m", "unittest", "test_split_pack", "test_bettermodel_pack")
}

val verifyBetterModel = tasks.register<Exec>("verifyBetterModel") {
    workingDir(layout.projectDirectory)
    commandLine("python3", "verify_bettermodel.py", "--source", packSource.absolutePath)
}

val packSplit = tasks.register<Exec>("packSplit") {
    dependsOn(verifyBetterModel, verifySplitPack)
    val target = layout.buildDirectory.dir("split")
    inputs.dir(packSource)
    inputs.file("split_pack.py")
    inputs.files(fileTree(packSource.parentFile.parentFile) { include("CREDITS.md") })
    outputs.files(listOf("base", "lobby", "modes", "starwars").map {
        target.map { directory -> directory.file("blockcraft-$it.zip") }
    })
    outputs.file(target.map { it.file("split-report.json") })
    workingDir(layout.projectDirectory)
    commandLine("python3", "split_pack.py", "--source", packSource.absolutePath,
        "--output", target.get().asFile.absolutePath)
}

tasks.check {
    dependsOn(verifyAssets, verifySplitPack, verifyBetterModel)
}

val pack = tasks.register<Zip>("pack") {
    dependsOn(verifyBetterModel, verifySplitPack)
    from(packSource)
    from("../CREDITS.md") {
        into("credits")
        var shipped = true
        filter { line ->
            if (line.startsWith("## ")) shipped = line.removePrefix("## ") in packCredits
            line.takeIf { shipped }
        }
    }
    archiveFileName = "blockcraft.zip"
    destinationDirectory = layout.buildDirectory
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.assemble {
    dependsOn(pack, packSplit)
}
