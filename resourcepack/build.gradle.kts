plugins {
    base
}

val generateTextures = tasks.register<Exec>("generateTextures") {
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

val generateDroids = tasks.register<Exec>("generateDroids") {
    workingDir(layout.projectDirectory)
    commandLine("java", "DroidArt.java", "src")
}

val generateCombat = tasks.register<Exec>("generateCombat") {
    workingDir(layout.projectDirectory)
    commandLine("java", "CombatArt.java", "src")
}

tasks.register("regenerate") {
    group = "build"
    description = "Regenerates textures, sounds, furniture, and vanilla item overrides in resourcepack/src."
    dependsOn(generateTextures, generateSounds, generateSternenfrontSounds, generateProps, generateDroids, generateCombat, ":paper:api:packOverrides")
}

val pack = tasks.register<Zip>("pack") {
    from("src")
    archiveFileName = "blockcraft.zip"
    destinationDirectory = layout.buildDirectory
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.assemble {
    dependsOn(pack)
}
