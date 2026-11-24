plugins {
    id("blockcraft.paper-plugin")
}

dependencies {
    compileOnly(project(":paper:api"))
    compileOnly(project(":paper:story"))
}
