# Tiered Echoes

A Forge mod for Minecraft 1.20.1 where gear gains experience as you use it and climbs a tier
ladder in return. Weapons, tools, armor, shields, ranged weapons, elytra and accessories each have
their own ladder, running Common, Uncommon, Rare, Epic.

## Progression

Experience comes from use. Weapons earn it from the damage they deal, tools from the blocks
they break, armor from the hits it takes, shields and elytra from the punishment they absorb.

Hovering a tiered item shows how far it is from its next tier, as `Experience: 45 / 150`.

An item upgrades once it holds enough experience for the next tier. It can also be upgraded by
hand at a smithing table, using a template and the tier's ingredient. A datapack decides which
of those routes is available; the default allows both. Every upgrade has a matching downgrade,
so a step back down costs nothing but the template.

Both routes keep what the item already carries: experience, enchantments, durability, and its
custom name. Material upgrades keep their tier too, so a diamond sword becoming netherite
stays Uncommon rather than reverting.

## Tiers

Each tier multiplies the item's stats. The built-in weapon ladder runs:

| Tier | Experience | Attack damage | Durability | Upgrade ingredient |
| --- | --- | --- | --- | --- |
| Common | 0 | 1.0x | 1.0x | — |
| Uncommon | 25 | 1.12x | 1.1x | Cobblestone |
| Rare | 75 | 1.25x | 1.2x | Iron ingot |
| Epic | 150 | 1.4x | 1.35x | Netherite ingot |

Armor, tools and accessories use the same four tiers with multipliers of their own. Accessories
additionally grant Curios set bonuses that rise with each matching piece equipped, reaching
1.24x at Epic with a full set.

Shields, ranged weapons and elytra scale durability only. None of them carry attributes in vanilla,
so declaring an attack damage multiplier on a bow would do nothing at all.

Tier ids are scoped by domain, so Rare on a sword and Rare on a chestplate are separate things,
and an armor piece can never follow a weapon branch.

Where a tier leads to more than one higher tier, an item picks a branch when it has enough
experience. You can choose one yourself instead, by shift-right-clicking the item while holding
a matching ingredient in your offhand.

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

Seven domains — `weapon`, `tool`, `armor`, `shield`, `ranged_weapons`, `elytra` and `accessory` —
each carrying the four tiers above, the matching upgrade and downgrade templates, and a base tag
for modded gear to join. Membership of a domain is decided by that tag and nothing else.

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
`#tieredechoes:weapons`, `#tieredechoes:tools`, `#tieredechoes:armor`, `#tieredechoes:shields`,
`#tieredechoes:ranged_weapons`, `#tieredechoes:elytra` and `#tieredechoes:accessories`. The tag is
the whole of the rule — an item in two tags climbs two ladders.

Pacing starts in `config/tieredechoes-common.toml`, which supplies the value each gamerule is
created with. Contributor documentation, including the build and release process, lives in
[docs/DEVELOPMENT.md](https://github.com/HexaHexes/tiered-echoes/blob/main/docs/DEVELOPMENT.md).

## Requirements

Minecraft 1.20.1 and Forge 47 or newer. No other mods are required.

## License

LGPL-2.1. See [LICENSE](https://github.com/HexaHexes/tiered-echoes/blob/main/LICENSE).
