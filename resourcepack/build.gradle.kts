plugins {
    base
}

// The client-side half of the network's look: menu panels and HUD icons as font glyphs, icon items and a blanked
// inventory label. The proxy serves and offers the zip (README, "Resource pack"); textures come from PackArt.java.
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
