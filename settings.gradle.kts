plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "blockcraft"

include(
    "common",
    "paper:api",
    "paper:bedwars",
    "paper:core",
    "paper:cores",
    "paper:duell",
    "paper:emeraldrush",
    "paper:endergames",
    "paper:lobby",
    "paper:mlgrush",
    "paper:quicksg",
    "paper:reports",
    "paper:skywars",
    "paper:statussigns",
    "paper:ttt",
    "paper:zombies",
    "velocity:system",
    "resourcepack",
)
