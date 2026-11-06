// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    // Downloads a missing JDK toolchain (21 for 1.21.1, 25 for 26.x)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "lifesmp"

include("core")
include("paper")
include("fabric")
// fabric-26/build.gradle.kts builds every Minecraft 26.x version; the project name carries the version
for (mc in listOf("26.2", "26.3")) {
    include("fabric-$mc")
    // Holds only build output, so Git does not keep the folder
    project(":fabric-$mc").projectDir = file("fabric-26/$mc").apply { mkdirs() }
    project(":fabric-$mc").buildFileName = "../build.gradle.kts"
}
