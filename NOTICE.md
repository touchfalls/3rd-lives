# Notice

## Life SMP
Copyright (C) 2026 touchfalls

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

---

## Minecraft & Platform Disclaimer

This project is not an official Minecraft product and is neither approved by nor associated with Mojang Studios or Microsoft Corporation. "Minecraft" is a registered trademark of Mojang Synergies AB.

This repository and its distribution binaries do NOT include or redistribute Minecraft code, Minecraft server jars, Paper server jars, or Fabric Loader server jars. All compilation dependencies on Minecraft, PaperMC, and Fabric APIs are resolved at build time via publicly available Maven repositories and Gradle development plugins (`paperweight-userdev` and `fabric-loom`).

---

## Third-Party Libraries and Licenses

The binary distributions of Life SMP (`lifesmp-<version>.jar` for Paper and `lifesmp-fabric-<version>.jar` for Fabric) bundle or link the following third-party open-source components:

### 1. HikariCP
- **Copyright:** (C) 2013-2024 Brett Wooldridge
- **License:** Apache License, Version 2.0
- **URL:** <https://github.com/brettwooldridge/HikariCP>
- **Packaging:**
  - Paper: Relocated and shaded into `io.github.lifesmp.libs.hikari`.
  - Fabric: Bundled via Jar-in-Jar (JiJ).

### 2. SQLite JDBC Driver
- **Copyright:** (C) 2006-2024 Taro L. Saito and SQLite-JDBC contributors
- **License:** Apache License, Version 2.0
- **URL:** <https://github.com/xerial/sqlite-jdbc>
- **Packaging:**
  - Paper: Shaded un-relocated (`org.sqlite`) to preserve native JNI library bindings.
  - Fabric: Bundled via Jar-in-Jar (JiJ).

### 3. Kyori Adventure & MiniMessage
- **Copyright:** (C) 2017-2024 KyoriPowered
- **License:** MIT License
- **URL:** <https://github.com/KyoriPowered/adventure>
- **Packaging:** Provided by Paper runtime; compile-only dependency.

### 4. SLF4J (Simple Logging Facade for Java)
- **Copyright:** (C) 2004-2024 QOS.ch
- **License:** MIT License
- **URL:** <https://www.slf4j.org/>
- **Packaging:** Provided by Paper and Fabric server runtimes; compile-only dependency.

### 5. Google Gson
- **Copyright:** (C) 2008-2024 Google Inc.
- **License:** Apache License, Version 2.0
- **URL:** <https://github.com/google/gson>
- **Packaging:** Provided by Fabric and Minecraft runtime; compile-only dependency.

### 6. JetBrains Java Annotations
- **Copyright:** (C) 2000-2024 JetBrains s.r.o.
- **License:** Apache License, Version 2.0
- **URL:** <https://github.com/JetBrains/java-annotations>
- **Packaging:** Compile-only annotations for static code analysis.

### 7. JUnit Jupiter & AssertJ (Test Scope Only)
- **Licenses:** Eclipse Public License 2.0 (JUnit) / Apache License 2.0 (AssertJ)
- **Packaging:** Strictly confined to test execution; not bundled in any distribution jar.
