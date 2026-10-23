// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

plugins {
    `java-library`
}

dependencies {
    // Database: SQLite & HikariCP
    api("org.xerial:sqlite-jdbc:3.45.3.0")
    api("com.zaxxer:HikariCP:5.1.0")

    // Logging & Annotations
    api("org.slf4j:slf4j-api:2.0.13")
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

tasks.jar {
    archiveBaseName.set("core")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}
