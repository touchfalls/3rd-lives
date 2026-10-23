## Summary

Describe what this pull request changes and why.

## Affected modules

- [ ] `:core`
- [ ] `:paper`
- [ ] `:fabric`
- [ ] Build / CI
- [ ] Documentation

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactoring (no behavior change)
- [ ] Documentation

## Checklist

- [ ] `./gradlew clean dist test` passes locally.
- [ ] `:core` still has no dependency on Paper, Fabric or Minecraft classes.
- [ ] No database IO on the server main thread.
- [ ] SQLite schema changes are added as a new migration in `SqliteSchemaMigrator`, existing migrations are unchanged.
- [ ] New config keys or messages are added to both `paper/src/main/resources/config.yml` and `fabric/src/main/resources/config.json`, and to the defaults in `LifeConfig`.
- [ ] New source files carry the SPDX header.
- [ ] `CHANGELOG.md` is updated under `[Unreleased]`.
- [ ] Tested on a server (state platform and version below), or not applicable.

## Testing

Platform, Minecraft version and steps used to test the change.

By submitting this pull request, I agree that my contribution is licensed under GPL-3.0-or-later.
