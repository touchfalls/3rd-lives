// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

// Minecraft 26.x is unobfuscated: no mappings and no remapping. Shares sources with :fabric.
plugins {
    `java-library`
    id("net.fabricmc.fabric-loom") version "1.19.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.srcDirs("../fabric/src/main/java", "../fabric/src/mc26_2/java")
        resources.srcDir("../fabric/src/main/resources")
    }
    test {
        java.srcDir("../fabric/src/test/java")
    }
}

dependencies {
    implementation(project(":core"))
    include(project(":core"))

    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")

    include("com.zaxxer:HikariCP:5.1.0")
    include("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation("org.jetbrains:annotations:24.1.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.24.0") // 5.12 cannot mock Java 25 classes
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

loom {
    mods {
        create("lifesmp") {
            sourceSet(sourceSets.getByName("main"))
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version, "minecraft" to "~26.2", "java" to "25", "loader" to "0.18.4")
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
    // No refmap: 26.x is unobfuscated, so mixin targets need no remapping
    filesMatching("lifesmp.mixins.json") {
        filter { line -> if (line.contains("\"refmap\"")) "" else line }
    }
}

tasks.jar {
    archiveBaseName.set("lifesmp-fabric-26")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Include compiled core classes, as :fabric does
    val coreJava = project(":core").extensions.getByType<JavaPluginExtension>()
    from(coreJava.sourceSets.getByName("main").output)
}
