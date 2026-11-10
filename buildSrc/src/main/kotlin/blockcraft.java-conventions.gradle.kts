plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("java").get().requiredVersion.toInt()

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000", "-Xlint:deprecation"))
}


dependencies {
    testImplementation(libs.findLibrary("junit").get())
}

configurations.named("testImplementation") {
    extendsFrom(configurations.getByName("compileOnly"))
}

tasks.withType<Test>().configureEach {
    useJUnit()
    inputs.property("databaseTestPort", providers.environmentVariable("BLOCKCRAFT_TEST_DB_PORT").orElse("disabled"))
}
