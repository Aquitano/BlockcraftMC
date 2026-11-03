plugins {
    id("blockcraft.paper-plugin")
}

dependencies {
    compileOnly(project(":paper:api"))
}

tasks.test {
    inputs.files(rootProject.fileTree("worlds/archive-exhibits") { include("*/exhibit.yml") })
}
