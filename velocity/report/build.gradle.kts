plugins {
    id("blockcraft.velocity-plugin")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.hikaricp)
    implementation(libs.mariadb.client)
}

tasks.shadowJar {
    relocate("com.zaxxer.hikari", "me.blockcraft.libs.hikari")
}
