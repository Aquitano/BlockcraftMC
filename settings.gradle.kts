plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "blockcraft"

include(
    "common",
    "paper:api",
    "paper:core",
    "paper:duell",
    "paper:emeraldrush",
    "paper:lobby",
    "paper:quicksg",
    "paper:reports",
    "paper:statussigns",
    "velocity:system",
    "resourcepack",
)
