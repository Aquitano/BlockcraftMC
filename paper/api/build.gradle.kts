plugins {
    id("blockcraft.paper-plugin")
    `java-library`
}

dependencies {
    api(project(":common"))
    compileOnly(libs.luckperms.api)
}
