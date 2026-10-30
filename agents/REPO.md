# Repo rules - Tiered Echoes

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in `neoforge-mod-template` and pulled in verbatim - **never hand-edit it.**
Everything in this file is this repo's own and is edited freely; keep it aligned
with the org rules rather than restating them.

**Repo:** `HexaHexes/tiered-echoes` · **Mod ID:** `tieredechoes` · **Branch:** `main`
**Platform:** Minecraft 1.20.1 / Forge 47+ / JDK 21 · **Jar:** `build/libs/tieredechoes-<version>.jar`

Gear that gets better as you use it. Weapons, tools and armor gain experience from
use, unlock tiers, and scale their stats as they climb.

**Platform note:** this repo predates the org's NeoForge 1.21.1 baseline - it stays
on Forge 1.20.1 (and the `tieredechoes` mod id without underscores) until a deliberate
port decision; the org naming and platform rules apply to it only on that port.

**Test:** `./gradlew runGameTestServer` runs the GameTest suite (JUnit 5 is wired;
`generateGameTestStructures` writes the empty `.nbt` structure templates the
GameTests are named after - run it when adding a GameTest, or the structure
assertions fail).

**Remote:** `github.com/HexaHexes/tiered-echoes` (public, default branch `main`).

---

## Maintenance mode

This repo is in maintenance mode (owner decision, 2026-10-30): **it keeps up with
the template but gets no new features of its own** until the mode lifts.

- Included in every template wave - rulebook pulls, dependency pins, CI and
  tooling updates, and new template content land here as they do everywhere.
- No repo-initiated feature work while the mode lasts.

Decision: `HexaHexes/neoforge-mod-template#52` (maintenance-mode note);
tracking: `HexaHexes/neoforge-mod-template#60`.
## CI & release model - this repo

- Ships the full workflow set: `ci.yml`, `release.yml` (version-tag release
  publishing), `version-bump.yml`, and `curseforge.yml`.
