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

tasks.register("regenerate") {
    group = "build"
    description = "Regenerates textures, sounds, and vanilla item overrides in resourcepack/src."
    dependsOn(generateTextures, generateSounds, ":paper:api:packOverrides")
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
