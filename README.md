# Tiered Echoes

A Forge 1.20.1 mod where equipment accrues experience and climbs a configurable tier ladder.
Progression happens two ways, and a datapack decides which are available:

- **Automatically** — using an item stores experience on it; once the current tier's requirement is
  met it upgrades.
- **Manually** — a smithing table upgrade, driven by a recipe derived from the tier path graph.

Both routes preserve stored experience, enchantments and durability. Material upgrades (for example
a diamond → netherite smithing recipe) keep the item's tier, because the tier lives in the item's
`TieredEchoes` tag rather than in a baked result item.

## Datapack format

Everything is data-driven. Drop files into a datapack under `data/<your_namespace>/`:

| Directory | Contents |
| --- | --- |
| `tiers/` | One JSON file per tier. |
| `tier_paths/` | The transition graph — which tiers lead where, and what each transition consumes. |
| `tier_templates/` | Which item occupies the smithing template slot for a tier. |
| `tier_policies/` | Progression rules for the whole pack. |

The mod ships its own defaults in `data/tieredechoes/…`, which are a working starting point to copy
and edit. Files shipped by a datapack silently override the built-ins of the same id.

### `tiers/<domain>/<tier>.json`

```json
{
  "schema_version": 1,
  "id": "tieredechoes:rare",
  "domain": "weapon",
  "display_rarity": "RARE",
  "sort_order": 2,
  "experience_required": 75,
  "generated_upgrade_template": true,
  "generated_downgrade_template": true,
  "upgrade_ingredient": { "item": "minecraft:diamond" },
  "downgrade_ingredient": { "item": "minecraft:iron_ingot" },
  "previous_tier": "tieredechoes:uncommon",
  "attribute_multipliers": { "attack_damage": 1.25 },
  "compat_amplifiers": {}
}
```

Tier ids are **not** globally unique — `tieredechoes:rare` exists once per domain. Every lookup is
scoped by `domain`, so an armour piece can never follow a weapon branch.

`domain` is one of `weapon`, `tool`, `armor`, `elytra`, `accessory`, `storage`. Which items belong
to a domain is decided by the item's class for the first four; `accessory` and `storage` exist for
compat and have no built-in ladder.

`attribute_multipliers` keys may be any attribute name. A `*2`-style key in `compat_amplifiers`
scales a compat-mod attribute.

### `tier_paths/<file>.json`

```json
{
  "schema_version": 1,
  "id": "tieredechoes:weapon_linear",
  "domain": "weapon",
  "edges": [
    {
      "from": "tieredechoes:uncommon",
      "to": "tieredechoes:rare",
      "display_branch_id": "weapon_uncommon_to_rare",
      "attunement_ingredient": { "item": "minecraft:diamond" },
      "downgrade_to": "tieredechoes:uncommon"
    }
  ]
}
```

`domain` may be declared once at the top level or per edge. If neither is present it is inferred
from the domain that actually declares the `from` tier — but only when that is unambiguous.

`attunement_ingredient` is what shift-right-click consumes to select this branch when several lead
out of the same tier, and what a generated smithing recipe uses as its addition. Its older alias,
`addition`, is also accepted.

Multiple edges sharing a `from` are **branching**: each becomes its own smithing recipe, and an
item that reaches a fork picks one when its experience fills, either at random or deterministically
(see `tier_policies`).

### `tier_templates/<domain>/<template>.json`

```json
{
  "schema_version": 1,
  "id": "tieredechoes:rare_weapon_upgrade_template",
  "tier": "tieredechoes:rare",
  "domain": "weapon",
  "kind": "upgrade",
  "item": "tieredechoes:rare_upgrade_template"
}
```

Declaring `item` lets a datapack add tiers without registering Java items — whatever you name here
is what the smithing table's template slot consumes. Without a declaration the built-in
`<tier>_upgrade_template` / `<tier>_downgrade_template` items are used when they exist.

### `tier_policies/<file>.json`

```json
{
  "schema_version": 1,
  "mode": "HYBRID",
  "multi_path_resolution": "RANDOM",
  "carry_over_excess_xp": true,
  "disable_upgrade_recipes": false,
  "consume_attunement_item": null
}
```

Files merge onto the defaults, so a two-line datapack can flip one rule.

| Field | Values | Effect |
| --- | --- | --- |
| `mode` | `HYBRID`, `AUTO_ONLY`, `SMITHING_ONLY` | `AUTO_ONLY` stops publishing upgrade recipes; `SMITHING_ONLY` disables experience-driven upgrading. Downgrades stay available in both. |
| `multi_path_resolution` | `RANDOM`, `FIRST` | How a branch is chosen at a fork when none is attuned. |
| `carry_over_excess_xp` | boolean | Whether xp beyond the requirement survives the upgrade. |
| `disable_upgrade_recipes` | boolean | Kill-switch layered on top of `mode`. |
| `consume_attunement_item` | boolean or omitted | `null`/omitted defers to the `item_xp.consume_attunement_item` config option. |

## Making modded gear tierable

Add the items to the base tag for their domain rather than writing a recipe:

- `#tieredechoes:weapon_bases`
- `#tieredechoes:tool_bases`
- `#tieredechoes:armor_bases`

These tags ship with the mod and are extended by the generated smithing recipes, so an item added
to one becomes tierable with no other changes.

## Recipes

Recipes of type `tieredechoes:tier_upgrade` and `tieredechoes:tier_downgrade` are generated from the
path graph on every reload and injected into the recipe manager, so they appear in the vanilla
smithing table without any further wiring. Their JSON shape matches `minecraft:smithing_transform`
plus `domain`, `source_tier` and `target_tier`:

```json
{
  "type": "tieredechoes:tier_upgrade",
  "template": { "item": "tieredechoes:rare_upgrade_template" },
  "base": { "tag": "tieredechoes:weapon_bases" },
  "addition": { "item": "minecraft:diamond" },
  "domain": "weapon",
  "source_tier": "tieredechoes:uncommon",
  "target_tier": "tieredechoes:rare"
}
```

Only hand-write these if you want a transition the path graph does not describe; ids under
`tier_upgrade/` and `tier_downgrade/` are owned by the generator and are replaced on each reload.

## Validation

Run `/tieredechoes validate` to re-check the loaded data, and watch the log for `[Validation]`
lines after a reload. Issues are reported with the file and transition involved.
