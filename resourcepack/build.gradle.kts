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
    val passed = layout.buildDirectory.file("asset-checks/passed")
    inputs.files(fileTree(layout.projectDirectory) {
        include("*.py", "*.java", "finished-weapons.txt", "pyproject.toml", "uv.lock")
    })
    inputs.dir("src")
    inputs.dir("bettermodel")
    inputs.dir("catalogs")
    inputs.dir("../nexo/items")
    inputs.file("../paper/api/src/main/java/me/blockcraft/api/pack/PackModel.java")
    inputs.file("../CREDITS.md")
    outputs.file(passed)
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("assets.py", "check"))
    doLast { passed.get().asFile.writeText("") }
}

val packSource = providers.gradleProperty("packSource").map { file(it) }.getOrElse(file("src"))

val verifySplitPack = tasks.register<Exec>("verifySplitPack") {
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("split_pack.py", "--source", packSource.absolutePath, "--check"))
}

val verifyBetterModel = tasks.register<Exec>("verifyBetterModel") {
    workingDir(layout.projectDirectory)
    commandLine(assetCommand("verify_bettermodel.py", "--source", packSource.absolutePath))
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
    commandLine(assetCommand("split_pack.py", "--source", packSource.absolutePath,
        "--output", target.get().asFile.absolutePath))
}

tasks.check {
    dependsOn(verifyAssets)
}

tasks.assemble {
    dependsOn(packSplit)
}
