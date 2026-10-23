# Contributing to Life SMP

Thank you for your interest in contributing to Life SMP! We welcome contributions, bug reports, and suggestions from the community.

## 1. Development Prerequisites

- **Java Development Kit (JDK):** Version 21 (Temurin or OpenJDK recommended)
- **Gradle:** 8.8+ or bundled Gradle Wrapper (`./gradlew`)
- **Git**

## 2. Building and Testing

Clone the repository and build the project using Gradle:

```bash
# Build all modules and run full test suite
./gradlew build

# Run tests across :core, :paper, and :fabric
./gradlew test

# Assemble distribution jars into dist/
./gradlew dist

# Verify jar structures, relocations, and service descriptors
./gradlew verifyJars

# Full clean verification
./gradlew clean dist test verifyJars
```

All 75+ automated unit and integration tests must pass cleanly before opening a pull request.

## 3. Code Style & Architecture Guidelines

- **Architecture Boundary:** The `:core` module must remain completely free of platform dependencies (Bukkit, Paper, Fabric, Minecraft, or Netty). Platform-specific behavior must reside strictly in `:paper` or `:fabric`.
- **Formatting:** Follow the repository [`.editorconfig`](.editorconfig) (UTF-8, LF line endings, 4 spaces for Java/Kotlin, 2 spaces for JSON/YAML).
- **Comments and Documentation:**
  - Write all code comments, JavaDoc, commit messages, and documentation in English.
  - Keep comments focused on *why* a particular design decision or workaround exists, rather than restating *what* the code does.
  - Avoid decorative comment banners, ASCII art, or developmental markers (`TODO`, `FIXME`). Place verified notes in `CHECKLIST.md`.
- **SPDX Headers:** Every new `.java` and `.gradle.kts` file must include the SPDX license header at the very top:
  ```java
  // SPDX-License-Identifier: GPL-3.0-or-later
  // Copyright (C) <YEAR> <AUTHOR>
  ```

## 4. Commit Message Guidelines

We follow the Conventional Commits specification:

```
<type>(<scope>): <short summary in imperative mood>

[optional detailed body]
```

- **Types:**
  - `feat`: A new feature or capability.
  - `fix`: A bug fix.
  - `refactor`: Code reorganization without behavior change.
  - `test`: Adding or updating test cases.
  - `docs`: Documentation updates or additions.
  - `chore`: Build scripts, dependencies, or maintenance.
- **Examples:**
  - `feat(core): implement dead tab capacity eviction strategy`
  - `fix(paper): resolve missing animal tamer import in combat listener`
  - `docs: update deployment guidelines for paper and fabric`

## 5. Pull Request Process

1. Fork the repository and create a descriptive feature branch:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Commit your changes with clear, concise messages.
3. Verify that `./gradlew clean dist test verifyJars` passes with zero errors and zero warnings.
4. Push to your fork and submit a Pull Request targeting the `main` branch.
5. Provide a clear description of the problem solved, changes made, and verification steps taken.

## 6. Contributor License Agreement

By submitting a pull request, patch, or any other contribution to this project, you agree that your contributions will be licensed under the terms of the **GNU General Public License, version 3.0 or later (GPL-3.0-or-later)**.
