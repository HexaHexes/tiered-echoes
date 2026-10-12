# Tiered Echoes

A Forge mod for Minecraft 1.20.1 where gear climbs a tier ladder at a smithing table. Weapons,
tools and armor each have their own ladder, running Common, Uncommon, Rare, Epic, Legendary.

## Progression

Tiers are gained by smithing, and by nothing else. An untiered item joins its ladder at a smithing
table with a template and the first rung's ingredient; each rung after that is another trip with
the next template and the next metal. A datapack decides which routes exist and what each one
costs.

Both the way up and the way down are switches a datapack declares and an operator can override per
world. The way down ships off.

A smithing upgrade keeps what the item already carries: enchantments, durability, and its custom
name. Material upgrades keep their tier too, so a diamond sword becoming netherite stays Uncommon
rather than reverting.

## Tiers

Each tier multiplies the item's stats. The built-in weapon ladder runs:

| Tier | Attack damage | Durability | Upgrade ingredient |
| --- | --- | --- | --- |
| Common | 1.0x | 1.0x | Copper ingot |
| Uncommon | 1.12x | 1.1x | Iron ingot |
| Rare | 1.25x | 1.2x | Gold ingot |
| Epic | 1.4x | 1.35x | Diamond |
| Legendary | 1.6x | 1.55x | Netherite ingot |

Armor and tools use the same five rungs with multipliers of their own.

Tier ids are scoped by domain, so Rare on a sword and Rare on a chestplate are separate things,
and an armor piece can never follow a weapon branch.

A legendary-tier item carries the `LEGENDARY` rarity, a golden orange above Epic.

## Gamerules

Five gamerules govern what a world does. Each one is an integer rather than a boolean, because the
useful state is the middle one:

| Value | Meaning |
| --- | --- |
| `0` | Follow `tier_policies` in the datapack. Every rule starts here. |
| `1` | Force the switch off, whatever the pack asked for. |
| `2` | Force the switch on, whatever the pack asked for. |

| Gamerule | Overrides |
| --- | --- |
| `teTiers` | Whether items carry a tier at all. |
| `teTierUpgrades` | Whether the smithing upgrade route is published. |
| `teLevels` | Whether items carry a level. |
| `teLevelUp` | Whether an item may gain a level. |
| `teDowngrades` | Whether the smithing downgrade route is published. |

There is no config default for any of these: a switch the datapack already answered does not need a
second source of truth, and a rule scoped to one world is reversible where an installation's config
file is not.

A system switch hides rather than deletes. `/gamerule teTiers 1` makes every tiered item present as
untiered, but the tag on the item stays put, and setting the rule back to `0` restores every bracket
and multiplier that was showing before.

A progression switch closes a route rather than rewinding progress. `/gamerule teTierUpgrades 1`
stops new upgrades being offered; an item already sitting on `legendary` stays there.

## Included

Three domains ship a complete ladder — `weapon`, `tool` and `armor` — each carrying the five
rungs above, an upgrade template apiece, and a base tag for modded gear to join. Membership of a
domain is decided by that tag and nothing else.

The domain list is longer than the shipped one. `shield`, `ranged_weapons`, `elytra` and
`accessory` remain fully supported as datapack targets — their constants, their tag paths and
their parsing are all intact — but the mod ships no ladder and no creative tab for them, because
those items scale durability and nothing else in vanilla. A pack that wants to give a modded bow
real attributes writes the ladder itself.

Tiered items show their tier in the name, as `Iron Sword [Uncommon]`.

| Command | Effect |
| --- | --- |
| `/tieredechoes inspect hand` | Show the item held and the tier it holds on each ladder. |
| `/tieredechoes validate` | Re-check the loaded tiers, paths and templates. |
| `/tieredechoes preview <domain> <tier>` | Preview a tier's stats. |

## Pack makers

Tiers, paths, templates and progression switches are all data. The mod ships a complete working set
you can copy and edit; see [docs/DATAPACK.md](https://github.com/HexaHexes/tiered-echoes/blob/main/docs/DATAPACK.md).

Modded items become tierable by adding them to the base tag for their domain:
`#tieredechoes:weapons`, `#tieredechoes:tools` and `#tieredechoes:armor`. The tag is the whole of
the rule — an item in two tags climbs two ladders. The shield, ranged, elytra and accessory tags
resolve too, so declaring a ladder for one of them is purely a data change.

Pacing starts in `tier_policies`, which a world can override one switch at a time with the
`te*` gamerules. Contributor documentation, including the build and release process, lives in
[docs/DEVELOPMENT.md](https://github.com/HexaHexes/tiered-echoes/blob/main/docs/DEVELOPMENT.md).

## Requirements

Minecraft 1.20.1 and Forge 47 or newer. No other mods are required.

## License

LGPL-2.1. See [LICENSE](https://github.com/HexaHexes/tiered-echoes/blob/main/LICENSE).
