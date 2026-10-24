// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

val modVersion: String = property("mod_version") as String

allprojects {
    group = "io.github.lifesmp"
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://libraries.minecraft.net/")
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        // Minecraft 1.21.1 target: Java 21 (:fabric-26 overrides it with Java 25)
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

tasks.register("dist") {
    group = "distribution"
    description = "Assembles Paper and Fabric distribution jars into dist/ directory"
    dependsOn(":paper:shadowJar", ":fabric:remapJar", ":fabric-26:jar")

    doLast {
        val distDir = layout.projectDirectory.dir("dist")
        val paperDist = distDir.dir("paper")
        val fabricDist = distDir.dir("fabric")
        val fabric26Dist = distDir.dir("fabric-26")

        distDir.asFile.deleteRecursively()
        paperDist.asFile.mkdirs()
        fabricDist.asFile.mkdirs()
        fabric26Dist.asFile.mkdirs()

        val paperLibsDir = project(":paper").layout.buildDirectory.dir("libs").get().asFile
        val paperJar = paperLibsDir.resolve("lifesmp-$modVersion.jar").let {
            if (it.exists()) it else project(":paper").tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile
        }
        val fabricJar = project(":fabric").tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").get().archiveFile.get().asFile

        paperJar.copyTo(paperDist.file("lifesmp-$modVersion.jar").asFile, overwrite = true)
        val fabric26Jar = project(":fabric-26").tasks.named<org.gradle.jvm.tasks.Jar>("jar").get().archiveFile.get().asFile
        fabricJar.copyTo(fabricDist.file(fabricJar.name).asFile, overwrite = true)
        fabric26Jar.copyTo(fabric26Dist.file(fabric26Jar.name).asFile, overwrite = true)

        logger.lifecycle("Dist artifacts assembled:")
        logger.lifecycle("  - Paper:  ${paperDist.file(paperJar.name).asFile.relativeTo(rootDir)}")
        logger.lifecycle("  - Fabric: ${fabricDist.file(fabricJar.name).asFile.relativeTo(rootDir)}")
        logger.lifecycle("  - Fabric 26.x: ${fabric26Dist.file(fabric26Jar.name).asFile.relativeTo(rootDir)}")
    }
}

tasks.register("verifyJars") {
    group = "verification"
    description = "Verifies integrity, dependencies, descriptors, and naming of Paper and Fabric jars"
    dependsOn("dist")

    doLast {
        val distDir = layout.projectDirectory.dir("dist")
        val paperJar = distDir.dir("paper").file("lifesmp-$modVersion.jar").asFile
        val fabricJars = listOf(
            distDir.dir("fabric").file("lifesmp-fabric-$modVersion.jar").asFile,
            distDir.dir("fabric-26").file("lifesmp-fabric-26-$modVersion.jar").asFile
        )

        if (!paperJar.exists()) {
            throw GradleException("Verification failed: Paper jar does not exist at ${paperJar.absolutePath}")
        }

        // 1. Verify Paper JAR
        if (paperJar.name.contains("SNAPSHOT", ignoreCase = true)) {
            throw GradleException("Verification failed: Paper jar name contains SNAPSHOT: ${paperJar.name}")
        }
        if (paperJar.name.contains("all", ignoreCase = true) || paperJar.name.startsWith("paper-")) {
            throw GradleException("Verification failed: Paper jar name is invalid: ${paperJar.name}")
        }

        java.util.zip.ZipFile(paperJar).use { zip ->
            // Check relocated HikariCP
            val relocatedHikari = zip.getEntry("io/github/lifesmp/libs/hikari/HikariConfig.class")
            if (relocatedHikari == null) {
                throw GradleException("Verification failed in Paper jar: missing relocated HikariCP class 'io/github/lifesmp/libs/hikari/HikariConfig.class'")
            }

            // Check NO un-relocated HikariCP
            val unrelocatedHikari = zip.getEntry("com/zaxxer/hikari/HikariConfig.class")
            if (unrelocatedHikari != null) {
                throw GradleException("Verification failed in Paper jar: found un-relocated HikariCP class 'com/zaxxer/hikari/HikariConfig.class'! Shading relocation failed.")
            }

            // Check sqlite-jdbc
            val sqliteDriverClass = zip.getEntry("org/sqlite/JDBC.class")
            if (sqliteDriverClass == null) {
                throw GradleException("Verification failed in Paper jar: missing sqlite-jdbc class 'org/sqlite/JDBC.class'")
            }

            // Check ServiceLoader java.sql.Driver
            val serviceDriver = zip.getEntry("META-INF/services/java.sql.Driver")
            if (serviceDriver == null) {
                throw GradleException("Verification failed in Paper jar: missing 'META-INF/services/java.sql.Driver'")
            }
            val driverContent = zip.getInputStream(serviceDriver).bufferedReader(Charsets.UTF_8).readText()
            if (!driverContent.contains("org.sqlite.JDBC")) {
                throw GradleException("Verification failed in Paper jar: 'META-INF/services/java.sql.Driver' does not contain 'org.sqlite.JDBC'. Content:\n$driverContent")
            }

            // Check bundled message files
            for (lang in listOf("en", "ru", "fr", "it", "es")) {
                if (zip.getEntry("lifesmp/lang/$lang.properties") == null) {
                    throw GradleException("Verification failed in Paper jar: missing 'lifesmp/lang/$lang.properties'")
                }
            }

            // Check paper-plugin.yml
            val pluginYml = zip.getEntry("paper-plugin.yml")
            if (pluginYml == null) {
                throw GradleException("Verification failed in Paper jar: missing 'paper-plugin.yml'")
            }
            val pluginYmlContent = zip.getInputStream(pluginYml).bufferedReader(Charsets.UTF_8).readText()
            if (!pluginYmlContent.contains("name: LifeSMP")) {
                throw GradleException("Verification failed in Paper jar: 'paper-plugin.yml' missing 'name: LifeSMP'")
            }
            if (!pluginYmlContent.contains("version: '$modVersion'") && !pluginYmlContent.contains("version: $modVersion") && !pluginYmlContent.contains("version: \"$modVersion\"")) {
                throw GradleException("Verification failed in Paper jar: 'paper-plugin.yml' missing expanded version '$modVersion'. Content:\n$pluginYmlContent")
            }
            if (!pluginYmlContent.contains("main: io.github.lifesmp.paper.LifeSmpPaperPlugin")) {
                throw GradleException("Verification failed in Paper jar: 'paper-plugin.yml' missing main entrypoint")
            }
        }

        // 2. Verify Fabric JARs (1.21.1 and 26.x)
        for (fabricJar in fabricJars) {
            if (!fabricJar.exists()) {
                throw GradleException("Verification failed: Fabric jar does not exist at ${fabricJar.absolutePath}")
            }
            if (fabricJar.name.contains("SNAPSHOT", ignoreCase = true)) {
                throw GradleException("Verification failed: Fabric jar name contains SNAPSHOT: ${fabricJar.name}")
            }
            if (fabricJar.name.contains("-dev.jar")) {
                throw GradleException("Verification failed: Fabric jar is dev jar: ${fabricJar.name}")
            }

            java.util.zip.ZipFile(fabricJar).use { zip ->
                // Check fabric.mod.json
                val modJsonEntry = zip.getEntry("fabric.mod.json")
                if (modJsonEntry == null) {
                    throw GradleException("Verification failed in Fabric jar: missing 'fabric.mod.json'")
                }
                val modJsonContent = zip.getInputStream(modJsonEntry).bufferedReader(Charsets.UTF_8).readText()
                if (!modJsonContent.contains("\"id\":\"lifesmp\"") && !modJsonContent.contains("\"id\": \"lifesmp\"")) {
                    throw GradleException("Verification failed in Fabric jar: 'fabric.mod.json' missing id 'lifesmp'")
                }
                if (!modJsonContent.contains("\"version\":\"$modVersion\"") && !modJsonContent.contains("\"version\": \"$modVersion\"")) {
                    throw GradleException("Verification failed in Fabric jar: 'fabric.mod.json' missing version '$modVersion'")
                }
                if (!modJsonContent.contains("io.github.lifesmp.fabric.LifeSmpFabricServer")) {
                    throw GradleException("Verification failed in Fabric jar: 'fabric.mod.json' missing server entrypoint 'io.github.lifesmp.fabric.LifeSmpFabricServer'")
                }

                // Check bundled message files
                for (lang in listOf("en", "ru", "fr", "it", "es")) {
                    if (zip.getEntry("lifesmp/lang/$lang.properties") == null) {
                        throw GradleException("Verification failed in Fabric jar: missing 'lifesmp/lang/$lang.properties'")
                    }
                }

                // Check lifesmp.mixins.json
                val mixinEntry = zip.getEntry("lifesmp.mixins.json")
                if (mixinEntry == null) {
                    throw GradleException("Verification failed in Fabric jar: missing 'lifesmp.mixins.json'")
                }

                // Check JiJ jars (or embedded classes)
                val entries = zip.entries().asSequence().map { it.name }.toList()
                val hasHikariJiJ = entries.any { it.startsWith("META-INF/jars/HikariCP") } || zip.getEntry("com/zaxxer/hikari/HikariConfig.class") != null
                if (!hasHikariJiJ) {
                    throw GradleException("Verification failed in Fabric jar: HikariCP is not present in META-INF/jars/ or as classes")
                }

                val hasSqliteJiJ = entries.any { it.startsWith("META-INF/jars/sqlite-jdbc") } || zip.getEntry("org/sqlite/JDBC.class") != null
                if (!hasSqliteJiJ) {
                    throw GradleException("Verification failed in Fabric jar: sqlite-jdbc is not present in META-INF/jars/ or as classes")
                }

                // Inspect nested sqlite-jdbc jar in JiJ
                val sqliteJijEntryName = entries.firstOrNull { it.startsWith("META-INF/jars/sqlite-jdbc") }
                if (sqliteJijEntryName != null) {
                    val sqliteJijEntry = zip.getEntry(sqliteJijEntryName)
                    val tempSqlite = java.io.File.createTempFile("sqlite-jij", ".jar")
                    try {
                        zip.getInputStream(sqliteJijEntry).use { input ->
                            tempSqlite.outputStream().use { output -> input.copyTo(output) }
                        }
                        java.util.zip.ZipFile(tempSqlite).use { nestedZip ->
                            if (nestedZip.getEntry("org/sqlite/JDBC.class") == null) {
                                throw GradleException("Verification failed in Fabric nested sqlite jar: missing 'org/sqlite/JDBC.class'")
                            }
                            val nestedService = nestedZip.getEntry("META-INF/services/java.sql.Driver")
                            if (nestedService == null) {
                                throw GradleException("Verification failed in Fabric nested sqlite jar: missing 'META-INF/services/java.sql.Driver'")
                            }
                        }
                    } finally {
                        tempSqlite.delete()
                    }
                }
            }
        }

        logger.lifecycle("SUCCESS: All verification checks passed for Paper and Fabric jars!")
        logger.lifecycle("  - Paper:  dist/paper/${paperJar.name} (relocated HikariCP, un-relocated sqlite-jdbc, ServiceLoader Driver, paper-plugin.yml)")
        logger.lifecycle("  - Fabric: ${fabricJars.joinToString { "dist/${it.parentFile.name}/${it.name}" }} (JiJ HikariCP, JiJ sqlite-jdbc, fabric.mod.json, mixins)")
    }
}
