plugins {
    id("blockcraft.paper-plugin")
}

dependencies {
    compileOnly(project(":paper:api"))
    compileOnly(libs.bettermodel.api)
}
