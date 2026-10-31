plugins {
    id("blockcraft.paper-plugin")
}

// Grim publishes no API artifact for the build the servers run, so core compiles against the API classes in that jar.
val grimApi = tasks.register<Jar>("grimApi") {
    val grim = rootProject.tasks.named("downloadGrimAC")
    dependsOn(grim)
    from({ zipTree(grim.get().outputs.files.singleFile) }) {
        include("ac/grim/grimac/api/**", "ac/grim/grimac/shaded/jetbrains/annotations/**")
    }
    archiveFileName = "grim-api.jar"
    destinationDirectory = layout.buildDirectory.dir("grim")
}

dependencies {
    compileOnly(project(":paper:api"))
    compileOnly(files(grimApi))
}
