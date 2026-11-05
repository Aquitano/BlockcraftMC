plugins {
    id("blockcraft.paper-plugin")
}

dependencies {
    compileOnly(project(":paper:api"))
    implementation(libs.glowingentities)
}

tasks.shadowJar {
    relocate("fr.skytasul", "me.blockcraft.sternenfront.libs.skytasul")
}
