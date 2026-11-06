plugins {
    base
}

val generateTextures = tasks.register<Exec>("generateTextures") {
    dependsOn("generateSternenfrontArt")
    workingDir(layout.projectDirectory)
    commandLine("java", "PackArt.java", "src")
}

val generateSounds = tasks.register<Exec>("generateSounds") {
    workingDir(layout.projectDirectory)
    commandLine("python3", "SoundArt.py")
}

val generateSternenfrontSounds = tasks.register<Exec>("generateSternenfrontSounds") {
    workingDir(layout.projectDirectory)
    commandLine("uv", "run", "SternenfrontSounds.py")
}

val generateProps = tasks.register<Exec>("generateProps") {
    workingDir(layout.projectDirectory)
    commandLine("java", "PropArt.java", "src", "../nexo")
}

val generateSternenfrontArt = tasks.register<Exec>("generateSternenfrontArt") {
    workingDir(layout.projectDirectory)
    commandLine("java", "SternenfrontArt.java", "src")
}

val generateYardArt = tasks.register<Exec>("generateYardArt") {
    workingDir(layout.projectDirectory)
    commandLine("java", "YardArt.java", "src", "build/yard/contact-sheet.png")
}

val generateHud = tasks.register<Exec>("generateHud") {
    workingDir(layout.projectDirectory)
    commandLine("java", "HudArt.java", "src")
}

val generateBattle = tasks.register<Exec>("generateBattle") {
    dependsOn(generateSternenfrontArt)
    workingDir(layout.projectDirectory)
    commandLine("python3", "BattleArt.py")
}

val generateDialogue = tasks.register<Exec>("generateDialogue") {
    workingDir(layout.projectDirectory)
    commandLine("java", "DialogueArt.java", "src", "../paper/api/src/main/resources/dialogue-glyphs.properties")
}

val generatePresentation = tasks.register<Exec>("generatePresentation") {
    workingDir(layout.projectDirectory)
    commandLine("java", "PresentationArt.java", "src")
}

tasks.register("regenerate") {
    group = "build"
    description = "Regenerates textures, sounds, furniture, and vanilla item overrides in resourcepack/src."
    dependsOn(generateTextures, generateSounds, generateSternenfrontSounds, generateProps, generateSternenfrontArt, generateYardArt, generateHud, generateBattle, generateDialogue, generatePresentation, ":paper:api:packOverrides")
}

// Players get credits for what the pack contains; the map provenance and open questions stay in the repository.
val packCredits = setOf("Music", "Sound effects", "Models and textures")

val pack = tasks.register<Zip>("pack") {
    from("src")
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
    dependsOn(pack)
}
