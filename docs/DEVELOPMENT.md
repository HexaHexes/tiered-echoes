# Development

Build, test and release notes for people working on Tiered Echoes. Player-facing
documentation is in the [README](../README.md); datapack authors want
[DATAPACK.md](DATAPACK.md); the rules for landing work are in
[CONTRIBUTING.md](../CONTRIBUTING.md).

## Building

```
gradlew build              # jar
gradlew test               # JUnit 5 suite
gradlew runGameTestServer  # Forge GameTests
gradlew runData            # regenerate the built-in datapack
```

## The shipped ladder

The ladder is authored once, in `ShippedLadder`, and `runData` writes it out as the JSON the
game actually loads:

```
src/generated/resources/data/tieredechoes/
    tiers/<domain>/<tier>.json
    tier_paths/<domain>/linear.json
    tier_templates/<tier>_<upgrade|downgrade>_template.json
    recipes/templates/<tier>_<upgrade|downgrade>_template_copy.json
```

That directory is committed and ships in the jar. It is not a cache, it is the mod's built-in
ladder. There is no copy of it in Java, so a datapack that omits a tier really does remove it
rather than having a fallback quietly restore it on the next reload.

`runData` is a manual step rather than part of `build`, because booting the registry to rewrite
36 small files makes every build about a minute slower. What keeps it honest is a
`ShippedLadderTest` that regenerates into a scratch directory and fails the build listing the
exact files that differ, so a stale commit cannot ship.

Edit `ShippedLadder`, run `runData`, commit the result.

## GameTest structures

GameTest structures live in `src/main/resources/data/tieredechoes/structures/`, deliberately
outside the generated directory. They are named after the tests that need them. Every test here
asserts on code rather than on blocks, so one identical empty box serves all of them; only the
file name differs.

If you add or rename a `@GameTest` method, regenerate them:

```
gradlew generateGameTestStructures
```

A `GameTestStructuresTest` in the JUnit suite fails with the exact list of missing files if
this is forgotten, so a renamed test never surfaces as an opaque game test server crash. It
derives the ids from the same class the generator does, so the two cannot drift apart about
which tests exist — they used to carry separate copies of the same pattern.

The generator writes the NBT through Minecraft's own `NbtIo` rather than encoding bytes by
hand, and runs as a `JavaExec` task against the main source set, so it needs no separate
runtime and cannot be run against a checkout whose Java does not match.

## Continuous integration

Every pull request runs three required checks:

| Check | Command | Asserts |
| --- | --- | --- |
| `junit` | `gradlew build` | Compiles, and all 142 JUnit tests pass |
| `gametest` | `gradlew runGameTestServer` | Logs `required tests passed` |
| `version-bump` | diff only | Version bump rules, and that breaking changes get a major or minor bump |

The GameTest check asserts on the log line rather than the task's exit code, because
`runGameTestServer` has been observed returning `BUILD SUCCESSFUL` after printing
`Failed to start the minecraft server` and running nothing at all.

`main` is protected: it accepts pull requests only, squash-merges only, and blocks force
pushes. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the full set of rules.

## Releases

Every push to `main` runs the release workflow, but a push only publishes when the change calls
for it — see [Turning off automatic alpha releases](#turning-off-automatic-alpha-releases).
`mod_version` in `gradle.properties` is the sole source of truth, and nothing bumps it for you.

| Change | Channel | Tag | CurseForge |
| --- | --- | --- | --- |
| Any work, no version bump | alpha | `v0.1.0+g7b31afe` | skipped |
| Work plus a bump with an `-alpha` suffix | alpha | `v0.2.0-alpha.1+g7b31afe` | skipped |
| Isolated bump with a `-beta` or `-rc` suffix | beta | `v0.2.0-beta.1` | beta |
| Isolated bump, no suffix | stable | `v0.2.0` | release |

Alphas are rolling builds of `main` and are published to GitHub only. A beta or stable bump
must change `gradle.properties` and nothing else; combined with any other change, CI rejects
it.

### Turning off automatic alpha releases

Alphas are **off unless enabled**. Set the `ALPHA_RELEASES` repository variable to `true`
under *Settings → Secrets and variables → Actions → Variables* to publish them; unset, or any
other value, skips the tag and the release. No workflow edit is needed to flip it.

The switch gates the release only, never the build. The build, JUnit suite and GameTests still
run on every push to `main`, which is deliberate: those are the only verification a merge
receives, since CI runs against the pull request rather than the merge. A push that would have
cut an alpha therefore still goes red if it breaks something, it simply publishes nothing.

Beta and stable are unaffected in either direction, so cutting a real release never depends on
the variable being set.

To cut a release, edit `mod_version`, open a PR containing only that change, and merge it. The
changelog is generated from commit subjects; for a stable release it spans back to the previous
stable tag, so `v0.2.0` lists everything since `v0.1.0`.
