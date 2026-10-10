# Tiered Echoes

A Forge 1.20.1 mod where the gear you use gets better at being used. Weapons, tools and armour
accrue experience as you fight, mine and take hits, and climb a tier ladder as they do — from
Common to Uncommon, Rare and Epic.

Progression happens two ways, and a datapack decides which are available:

- **Automatically.** Use an item, earn experience on it. Once the next tier's requirement is met
  the item upgrades by itself.
- **Manually.** Take the item to a smithing table with the right template and addition.

Both routes preserve what the item already carries: stored experience, enchantments, durability and
its custom name. That includes *material* upgrades — a diamond sword becoming netherite keeps its
tier, because the tier lives in the item's own `TieredEchoes` tag rather than in a baked result
item.

## Features

**Tier ladders per domain.** Weapons, tools, armour and accessories each climb their own ladder, and
tier ids are scoped by domain — so `rare` on a sword and `rare` on a chestplate are separate things
and an armour piece can never follow a weapon branch.

**Branching paths.** A tier can lead to more than one higher tier. When a fork is reached the item
picks a branch, or the player chooses one explicitly by shift-right-clicking with a matching
ingredient in the offhand.

**Downgrades are built in.** Every upgrade route has a way back down, using a downgrade template.
Stepping back down does not cost the item its experience or its enchantments.

**Smithing recipes derived from the graph.** A datapack describes the tier graph once; every
transition becomes a smithing table recipe on the next reload, with no extra wiring. Add a branch to
the graph and its recipe appears.

**A visible tier.** Tiered items show their tier in the hover name — `Iron Sword [Uncommon]`. The
suffix is composed at render time rather than written into the item, so it survives renames, never
stale, and is not baked in one language.

**Per-tier stat scaling.** Each tier can multiply attributes — attack damage, durability, armour,
and so on — as well as Curios set bonuses and compat-mod attributes.

**Nothing is hard-coded.** Tiers, paths, templates and progression rules all live in data. The mod
ships a complete working set you can copy and edit; see [docs/DATAPACK.md](docs/DATAPACK.md).

## What ships

Four domains — `weapon`, `tool`, `armor` and `accessory` — each with a Common → Uncommon → Rare →
Epic ladder, the matching upgrade and downgrade templates (craftable, and obtainable in creative),
and base tags for modded gear to join.

## Commands

| Command | Effect |
| --- | --- |
| `/tieredechoes inspect hand` | Show the tier, experience and selected branch of the item you are holding. |
| `/tieredechoes validate` | Re-check the loaded tiers, paths and templates. |
| `/tieredechoes preview <domain> <tier>` | Preview a tier's stats without changing anything. |

## Configuration

Six options, in `config/tieredechoes-common.toml`.

| Option | Default | What it does |
| --- | --- | --- |
| `item_xp.enabled` | `true` | Master switch for item experience. Turning it off leaves smithing progression intact. |
| `item_xp.auto_upgrade_single_path` | `true` | Whether an item upgrades automatically when only one path leads onward. Off means every upgrade is a smithing table decision. |
| `item_xp.consume_attunement_item` | `false` | Whether shift-right-click attunement eats the offhand ingredient. A `tier_policies` file that states `consume_attunement_item` takes precedence. |
| `debug.write_validation_json` | `true` | Write `tieredechoes-validation-report.json` to the game directory on reload. |
| `debug.compat_audit_mode` | `false` | Log detected compat capabilities and bridge activation. |
| `debug.compat_audit_verbose` | `false` | More detail on the above. |

The first three are pacing choices an operator may want to tune without writing a datapack; the
rules that decide *which* route progression takes belong in `tier_policies` instead.

## Making modded gear tierable

Add the items to the base tag for their domain — `#tieredechoes:weapon_bases`,
`#tieredechoes:tool_bases`, `#tieredechoes:armor_bases` and so on. These tags ship with the mod, so
an item added to one becomes tierable with no other changes.

## Development

```
gradlew build          # jar
gradlew test           # JUnit 5 suite
gradlew runGameTestServer   # Forge GameTests
gradlew runData        # regenerate the built-in datapack
```

The ladder is authored once, in `ShippedLadder`, and `runData` writes it out as the JSON the game
actually loads:

```
src/generated/resources/data/tieredechoes/
    tiers/<domain>/<tier>.json
    tier_paths/<domain>/linear.json
    tier_templates/<tier>_<upgrade|downgrade>_template.json
    recipes/templates/<tier>_<upgrade|downgrade>_template_copy.json
```

That directory is committed and ships in the jar. It is not a cache — it *is* the mod's built-in
ladder. There is no copy of it in Java, so a datapack that omits a tier really does remove it rather
than having a fallback quietly restore it on the next reload.

`runData` is a manual step rather than part of `build`, because booting the registry to rewrite 36
small files makes every build about a minute slower. What keeps it honest is a `ShippedLadderTest`
that regenerates into a scratch directory and fails the build listing the exact files that differ,
so a stale commit cannot ship. Edit `ShippedLadder`, run `runData`, commit the result.

GameTest structures live in `src/main/resources/data/tieredechoes/structures/` — hand-authored, and
deliberately outside the generated directory. They are named after the tests that need them. If you
add or rename a `@GameTest` method, regenerate them:

```
powershell tools/generate-gametest-structures.ps1
```

The script is ASCII-only on purpose, so it runs under Windows PowerShell 5.1 as well as PowerShell 7
if you have it.

A `GameTestStructuresTest` in the JUnit suite fails with the exact list of missing files if this is
forgotten, so a renamed test never surfaces as an opaque game test server crash.

## Continuous integration

Every pull request runs three required checks:

| Check | Command | Asserts |
| --- | --- | --- |
| `junit` | `gradlew build` | Compiles, and all 142 JUnit tests pass |
| `gametest` | `gradlew runGameTestServer` | Logs `required tests passed` |
| `version-bump` | diff only | Version bump rules, and that breaking changes get a major or minor bump |

Run both suites locally before pushing. The GameTest check asserts on the log line rather than the
task's exit code, because `runGameTestServer` has been observed returning `BUILD SUCCESSFUL` after
printing `Failed to start the minecraft server` and running nothing at all.

`main` is protected: it accepts pull requests only, squash-merges only, and blocks force pushes.
See [CONTRIBUTING.md](CONTRIBUTING.md) for the full set of rules.

## Releases

Every push to `main` publishes a release automatically. The kind depends on what the PR did —
`mod_version` in `gradle.properties` is the sole source of truth, and nothing bumps it for you.

| Change | Channel | Tag | CurseForge |
| --- | --- | --- | --- |
| Any work, no version bump | alpha | `v0.1.0+g7b31afe` | skipped |
| Work plus a bump with an `-alpha` suffix | alpha | `v0.2.0-alpha.1+g7b31afe` | skipped |
| Isolated bump with a `-beta` or `-rc` suffix | beta | `v0.2.0-beta.1` | beta |
| Isolated bump, no suffix | stable | `v0.2.0` | release |

Alphas are rolling builds of `main` and are published to GitHub only. A beta or stable bump must
change `gradle.properties` and nothing else; combined with any other change, CI rejects it.

To cut a release, edit `mod_version`, open a PR containing only that change, and merge it. The
changelog is generated from commit subjects — for a stable release it spans back to the previous
stable tag, so `v0.2.0` lists everything since `v0.1.0`.

## License

All Rights Reserved.
