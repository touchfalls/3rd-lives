// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.paperweight.userdev") version "1.7.4"
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    implementation(project(":core"))

    // Paper API for Minecraft 1.21.1
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Adventure and MiniMessage provided by Paper runtime
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    // Test dependencies
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("lifesmp")
    archiveClassifier.set("plain")
    archiveVersion.set(project.version.toString())
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("lifesmp")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // Relocate HikariCP to prevent runtime classpath clashes with other plugins
    relocate("com.zaxxer.hikari", "io.github.lifesmp.libs.hikari")

    // sqlite-jdbc MUST NOT be relocated: native JNI binaries require "org.sqlite" package name!
    // Exclude SLF4J API because Paper server runtime provides slf4j-api
    dependencies {
        exclude(dependency("org.slf4j:.*"))
    }

    // Merge JDBC ServiceLoader descriptors
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()

    // Clean up signatures, module metadata, and extra packaging files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
    exclude("kotlin/**")
    exclude("**/*.kotlin_metadata")
    exclude("**/*.kotlin_builtins")
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}
