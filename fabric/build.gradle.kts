// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

plugins {
    `java-library`
    id("fabric-loom") version "1.19.2"
}


dependencies {
    implementation(project(":core"))
    include(project(":core"))

    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:0.16.9")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.107.0+1.21.1")

    // Bundled SQLite & HikariCP via JiJ (include)
    include("com.zaxxer:HikariCP:5.1.0")
    include("org.xerial:sqlite-jdbc:3.45.3.0")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Test dependencies
    testImplementation(project(":core"))
    testImplementation("org.jetbrains:annotations:24.1.0")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.45.3.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

loom {
    mixin {
        useLegacyMixinAp.set(true)
        defaultRefmapName.set("lifesmp.refmap.json")
    }

    mods {
        create("lifesmp") {
            sourceSet(sourceSets.getByName("main"))
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("lifesmp-fabric")
    archiveClassifier.set("dev")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Include compiled core classes
    val coreJava = project(":core").extensions.getByType<JavaPluginExtension>()
    from(coreJava.sourceSets.getByName("main").output)
}

tasks.remapJar {
    archiveBaseName.set("lifesmp-fabric")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}
