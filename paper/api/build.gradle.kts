plugins {
    id("blockcraft.paper-plugin")
    `java-library`
}

dependencies {
    api(project(":common"))
    implementation(libs.hikaricp)
    implementation(libs.mariadb.client)
    compileOnly(libs.luckperms.api)
}

// Rewrites the pack's overrides of vanilla items from MenuIcon and PackModel. The vanilla client jar supplies each
// item's own definition as the fallback: ./gradlew :paper:api:packOverrides -PclientJar=<minecraft client jar>.
tasks.register<JavaExec>("packOverrides") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "me.blockcraft.api.pack.PackOverrides"
    val clientJar = providers.gradleProperty("clientJar")
    val pack = rootProject.layout.projectDirectory.dir("resourcepack/src").asFile.absolutePath
    argumentProviders.add(CommandLineArgumentProvider { listOf(clientJar.get(), pack) })
}

// The server ships slf4j; Hikari's copy would only shadow it.
tasks.shadowJar {
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}
