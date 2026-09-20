plugins {
    base
}

// The proxy serves this pack; PackArt.java and SoundArt.py regenerate its textures and audio.
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
