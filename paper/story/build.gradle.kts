plugins {
    id("blockcraft.paper-plugin")
}

repositories {
    maven("https://repo.betonquest.org/betonquest/")
}

dependencies {
    compileOnly(project(":paper:api"))
    compileOnly(libs.bettermodel.api)
    // The API module alone lacks DefaultObjective and NpcInteractCatcher, which live in core.
    compileOnly(libs.bundles.betonquest) {
        isTransitive = false
    }
}
