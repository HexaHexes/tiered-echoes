# Tiered Echoes

A Forge mod for Minecraft 1.20.1 where gear gains experience as you use it and climbs a tier
ladder in return. Weapons, tools and armor each have their own ladder, running Common, Uncommon,
Rare, Epic, Legendary.

## Progression

Experience comes from use. Weapons earn it from the damage they deal, tools from the blocks
they break, armor from the hits it takes.

Hovering a tiered item shows how far it is from its next tier, as `Experience: 45 / 200`.

An item upgrades once it holds enough experience for the next tier. It can also be upgraded by
hand at a smithing table, using a template and the tier's ingredient. A datapack decides which
of those routes is available; the default allows both. An untiered item joins the ladder through
the same table, spending the Common template and a copper ingot.

Both routes keep what the item already carries: experience, enchantments, durability, and its
custom name. Material upgrades keep their tier too, so a diamond sword becoming netherite
stays Uncommon rather than reverting.

## Tiers

Each tier multiplies the item's stats. The built-in weapon ladder runs:

| Tier | Experience | Attack damage | Durability | Upgrade ingredient |
| --- | --- | --- | --- | --- |
| Common | 25 | 1.0x | 1.0x | Copper ingot |
| Uncommon | 200 | 1.12x | 1.1x | Iron ingot |
| Rare | 675 | 1.25x | 1.2x | Gold ingot |
| Epic | 1600 | 1.4x | 1.35x | Diamond |
| Legendary | 3125 | 1.6x | 1.55x | Netherite ingot |

The experience column is what each step costs, so the whole ladder takes 5625 experience to
climb from nothing. The curve is cubic rather than geometric: the opening is cheap and the
weight sits on the later rungs, which is where a player has already committed to an item.

Armor and tools use the same five rungs with multipliers of their own.

Tier ids are scoped by domain, so Rare on a sword and Rare on a chestplate are separate things,
and an armor piece can never follow a weapon branch.

A legendary-tier item carries the `LEGENDARY` rarity, a golden orange above Epic.

## Gamerules

Three gamerules control how progression behaves in a world. Each one starts from the matching
value in `config/tieredechoes-common.toml`, so the config file is what a new world is created
with. After that the gamerule is what you change, per world, with `/gamerule`.

| Gamerule | Default | Effect |
| --- | --- | --- |
| `teItemExperience` | `true` | Whether items earn experience at all. Off leaves smithing progression intact. |
| `teAutoUpgrade` | `true` | Whether experience alone advances an item's tier. Off makes the smithing table the only route. |
| `teResetExperienceOnUpgrade` | `false` | Whether an upgrade spends the item's experience, rather than carrying the excess into the new tier. |

Editing the config file does not change worlds that already exist, which is the point. Pacing is
a decision about a world rather than about an installation.

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
| `/tieredechoes inspect hand` | Show the tier, experience and selected branch of the item held. |
| `/tieredechoes validate` | Re-check the loaded tiers, paths and templates. |
| `/tieredechoes preview <domain> <tier>` | Preview a tier's stats. |

## Pack makers

Tiers, paths, templates and progression rules are all data. The mod ships a complete working set
you can copy and edit; see [docs/DATAPACK.md](https://github.com/HexaHexes/tiered-echoes/blob/main/docs/DATAPACK.md).

Modded items become tierable by adding them to the base tag for their domain:
`#tieredechoes:weapons`, `#tieredechoes:tools` and `#tieredechoes:armor`. The tag is the whole of
the rule — an item in two tags climbs two ladders. The shield, ranged, elytra and accessory tags
resolve too, so declaring a ladder for one of them is purely a data change.

Pacing starts in `config/tieredechoes-common.toml`, which supplies the value each gamerule is
created with. Contributor documentation, including the build and release process, lives in
[docs/DEVELOPMENT.md](https://github.com/HexaHexes/tiered-echoes/blob/main/docs/DEVELOPMENT.md).

## Requirements

Minecraft 1.20.1 and Forge 47 or newer. No other mods are required.

## License

LGPL-2.1. See [LICENSE](https://github.com/HexaHexes/tiered-echoes/blob/main/LICENSE).
