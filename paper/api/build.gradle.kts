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

// The server ships slf4j; Hikari's copy would only shadow it.
tasks.shadowJar {
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}
