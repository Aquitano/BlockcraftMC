import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone")
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

tasks.compileJava {
    options.errorprone {
        // Default warnings flag intentional identity checks, detached futures and nested map votes.
        disableAllWarnings = true
        error(
            "EqualsBrokenForNull",
            "ExpensiveLenientFormatString",
            "InputStreamSlowMultibyteRead",
            "NullOptional",
            "OptionalNotPresent",
            "StreamResourceLeak",
        )
    }
}

tasks.compileTestJava {
    options.errorprone.enabled = false
}

dependencies {
    add("errorprone", libs.findLibrary("error-prone-core").get())
    testImplementation(libs.findLibrary("junit").get())
}

configurations.named("testImplementation") {
    extendsFrom(configurations.getByName("compileOnly"))
}

tasks.withType<Test>().configureEach {
    useJUnit()
    inputs.property("databaseTestPort", providers.environmentVariable("BLOCKCRAFT_TEST_DB_PORT").orElse("disabled"))
}
