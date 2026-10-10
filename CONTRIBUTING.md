# Contributing to Tiered Echoes

This document is the contract for how work lands on `main`. The rules here are
backed by CI wherever CI can enforce them, and stated as norms wherever it
cannot.

## Branches

| Prefix | Use |
| --- | --- |
| `feature/` | New capability |
| `fix/` | Correcting existing behaviour |
| `docs/` | Documentation only |
| `chore/` | Build, CI, tooling, refactors with no user-facing effect |

## Pull requests

**One logical changeset per PR.** A PR that does two unrelated things cannot be
reviewed as a unit and cannot be reverted cleanly. Split it.

Every PR is **squash-merged**, so a PR becomes exactly one commit on `main`.
This means granularity lives in your PRs, not in `main`'s log — a PR carrying
five commits still lands as a single commit. Write the PR title as a
sentence-case imperative, because the squash commit's subject line is taken
from it.

`main` is protected by rulesets, so these rules are enforced by GitHub rather
than by agreement:

- **Everything lands through a PR.** No direct pushes, no force-pushes, and
  `main` cannot be deleted. This holds for maintainers too.
- **All three CI checks must be green** — `junit`, `gametest`, `version-bump`.
- **Squash only.** Merge commits and rebases are disabled, so the one-PR-one-
  commit rule above cannot be broken by picking the wrong button.
- **Head branch is deleted automatically** once the PR merges.
- **PRs from contributors without write access need one approving review**,
  from anyone with write access.

That last rule has a deliberate exception worth understanding before it
surprises you. Maintainers are on the ruleset's **bypass list**, so they may
merge their own PRs without waiting for approval. GitHub does not make this
look calm: the PR still shows red and reads `REVIEW_REQUIRED`, because GitHub
refuses to treat an author as their own reviewer even when a rule is
explicitly bypassed. Merging takes the deliberate "merge without waiting for
requirements to be met" action. The red is expected, not a sign that
something is broken.

The exception covers *who* may merge, never *what* passes. CI is a separate
ruleset with no bypass list, so it gates every PR identically — a maintainer's
own PR included.

Before pushing, run both checks locally. CI is the gate, and a red PR costs a
round trip:

```
./gradlew build
./gradlew runGameTestServer
```

The second one needs care. **Its exit code is not a reliable pass signal** — a
run has printed `Failed to start the minecraft server` and returned
`BUILD SUCCESSFUL` without executing any tests. Assert on the log line
`required tests passed` instead of trusting the exit code.

## Documentation

Every feature or change must be documented. Where it is documented depends on
who the change is for:

| Audience | Location |
| --- | --- |
| Players and server operators | `README.md` |
| Datapack authors | `docs/DATAPACK.md` |
| Contributors | this file |

If a change is user-facing at all, it belongs in `README.md`. Internal
refactors and test-only changes do not, and should not pad the README to look
busy.

## Breaking changes

A change is **breaking** if it alters the datapack schema, item or NBT format,
or any public API in a way that invalidates existing content.

CI cannot detect a breaking change. No automated check can. It has to be
declared, by applying the **`breaking`** label to the PR before merge.

What the label does: any PR labelled `breaking` merged since the last stable
release forces the next **beta or stable** version bump to raise the major or
minor version. A breaking change cannot ship as a patch release, so
`0.1.0` → `0.1.1` would be rejected while a break is outstanding.

The gap to be honest about: **an unlabelled breaking change passes silently.**
Nothing catches it. That is a review norm, not a mechanism, and it depends on
reviewers asking the question.

## Versions and releases

`mod_version` in `gradle.properties` is the sole source of truth. Nothing bumps
it automatically — a release only happens when someone edits that file.

Every push to `main` creates a release. Which kind depends entirely on what the
PR did:

| PR content | New `mod_version` | Channel | Tag | CurseForge |
| --- | --- | --- | --- | --- |
| Any changes, no bump | unchanged | alpha | `v0.1.0+g7b31afe` | skipped |
| Changes + bump, `-alpha` suffix | `0.2.0-alpha.1` | alpha | `v0.2.0-alpha.1+g7b31afe` | skipped |
| Changes + bump, no or other suffix | — | **rejected by CI** | — | — |
| Bump only, `-alpha` | `0.2.0-alpha.1` | alpha | `v0.2.0-alpha.1+g7b31afe` | skipped |
| Bump only, `-beta` / `-rc` | `0.2.0-beta.1` | beta | `v0.2.0-beta.1` | beta |
| Bump only, no suffix | `0.2.0` | **stable** | `v0.2.0` | release |

Two rules decide everything:

1. **A clean version bump is the only thing that makes a stable release.**
   Beta and stable bumps must change `gradle.properties` and nothing else, so
   a release PR is unambiguous to reviewers.
2. **Alphas are the default state.** Anything that is not an isolated clean bump
   is an alpha, and an `-alpha` suffix may be bundled with the work that starts
   that cycle — no empty PR required just to move a version number.

Alphas always carry the commit hash; betas and stables never do. Two PRs can
merge while `mod_version` sits at `0.2.0-alpha.1`, so those releases are only
distinguishable by commit. Betas and stables are isolated one-per-version by
construction and need no hash.

### Cutting a release

1. Edit `mod_version` in `gradle.properties`.
2. Open a PR containing **only** that change.
3. Merge it.

For a beta, use a pre-release suffix: `0.2.0-beta.1`. For a stable release, use
a clean version: `0.2.0`.

Note that a clean bump publishes a stable release immediately, and its
changelog spans everything back to the previous stable tag. Betas published in
the meantime appear on that release page too, because a stable release is
cumulative.

## Changelog

Changelogs are generated from commit subjects, not maintained by hand.

- **Alpha** — commits since the latest tag of any kind. Normally one PR.
- **Beta and stable** — commits since the last *stable* tag, so `v0.2.0` lists
  everything since `v0.1.0` rather than only since the last beta.

Because PR titles become commit subjects, the PR title is what appears in the
changelog. Write it as a sentence-case imperative: *Add smithing template
items*, not *added smithing template items!!*.

## What CI enforces

Three checks are required on every PR:

| Check | Asserts |
| --- | --- |
| `junit` | `gradlew build` compiles and all 142 JUnit tests pass |
| `gametest` | `gradlew runGameTestServer` logs `required tests passed` |
| `version-bump` | Version bump rules above, plus the breaking-change magnitude rule |

## Repository hygiene

Never commit these. `.gitignore` covers them, but check before adding new
paths:

- `logs/`, `crash-reports/`, `screenshots/`
- `run/`, `run-data/`
- `src/generated/resources/.cache`

Generated datapack resources under `src/generated/resources` **are** committed —
they are the shipped ladder. Regenerate them with `./gradlew runData`, which is
a manual step and deliberately not part of `build`.
