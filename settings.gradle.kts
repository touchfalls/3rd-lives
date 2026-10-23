// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
}

rootProject.name = "lifesmp"

include("core")
include("paper")
include("fabric")
