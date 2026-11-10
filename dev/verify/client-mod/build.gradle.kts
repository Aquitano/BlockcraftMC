plugins {
    id("net.fabricmc.fabric-loom") version "1.18.4"
}

// The container keeps its own build output, so a build on the host never waits on the running client's locks.
providers.environmentVariable("VERIFY_BUILD_DIR").orNull?.let { layout.buildDirectory = file(it) }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

loom {
    accessWidenerPath = file("src/main/resources/verify.classtweaker")
    runs {
        named("client") {
            programArgs("--username", providers.environmentVariable("VERIFY_USERNAME").getOrElse("Verify"), "--width", "960", "--height", "540")
            vmArgs("-Xmx${providers.environmentVariable("VERIFY_CLIENT_HEAP").getOrElse("2G")}")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.2+26.3")
}
