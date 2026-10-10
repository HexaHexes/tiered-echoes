$ErrorActionPreference = 'Stop'

# Regenerates the built-in datapack that ships inside the mod jar:
#   data/tieredechoes/tiers/<domain>/<tier>.json
#   data/tieredechoes/tier_paths/<domain>/linear.json
#   data/tieredechoes/tier_templates/<domain>/<tier>_<kind>_template.json
#
# The values mirror TierService.builtinDefaults()/PathService.builtinEdges(); the JSON is the
# authoring surface and silently overrides the Java fallback on reload.

$root = Join-Path $PSScriptRoot '..\src\main\resources\data\tieredechoes'
$root = (Resolve-Path $root).Path

$domains = @('weapon', 'tool', 'armor', 'accessory')

$upgrades   = @{ weapon='minecraft:cobblestone'; tool='minecraft:cobblestone'; armor='minecraft:cobblestone'; accessory='minecraft:gold_ingot' }
$step1      = @{ weapon='minecraft:iron_ingot';  tool='minecraft:iron_ingot';  armor='minecraft:iron_ingot';  accessory='minecraft:emerald' }
$step2      = @{ weapon='minecraft:diamond';     tool='minecraft:diamond';     armor='minecraft:diamond';     accessory='minecraft:diamond' }
$step3      = @{ weapon='minecraft:netherite_ingot'; tool='minecraft:netherite_ingot'; armor='minecraft:netherite_ingot'; accessory='minecraft:nether_star' }

$tiers = @('common', 'uncommon', 'rare', 'epic')
$rarities = @('COMMON', 'UNCOMMON', 'RARE', 'EPIC')
$sorts = @(0, 1, 2, 3)
$xp = @(0, 25, 75, 150)

$multipliers = @(
    '{"durability":1.0,"attack_damage":1.0,"attack_speed":1.0,"armor":1.0,"armor_toughness":1.0,"knockback_resistance":1.0,"max_mana":1.0,"mana_regen":1.0,"cold_resistance":1.0,"heat_resistance":1.0,"thermal_resistance":1.0}',
    '{"durability":1.1,"attack_damage":1.12,"attack_speed":1.04,"armor":1.12,"armor_toughness":1.1,"knockback_resistance":1.08,"max_mana":1.12,"mana_regen":1.08,"cold_resistance":1.12,"heat_resistance":1.12,"thermal_resistance":1.12}',
    '{"durability":1.2,"attack_damage":1.25,"attack_speed":1.08,"armor":1.25,"armor_toughness":1.2,"knockback_resistance":1.15,"max_mana":1.25,"mana_regen":1.15,"cold_resistance":1.25,"heat_resistance":1.25,"thermal_resistance":1.25}',
    '{"durability":1.35,"attack_damage":1.4,"attack_speed":1.12,"armor":1.4,"armor_toughness":1.35,"knockback_resistance":1.25,"max_mana":1.4,"mana_regen":1.25,"cold_resistance":1.4,"heat_resistance":1.4,"thermal_resistance":1.4}'
)

$setBonuses = @(
    '{"*2":1.05}',
    '{"*2":1.07,"*3":1.1}',
    '{"*2":1.1,"*3":1.15,"*4":1.2}',
    '{"*2":1.12,"*3":1.18,"*4":1.24}'
)

function Write-Utf8NoBom([string]$path, [string]$content) {
    $dir = Split-Path $path -Parent
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
}

function Remove-DirIfPresent([string]$path) {
    if (Test-Path $path) { Remove-Item -Recurse -Force $path }
}

Remove-DirIfPresent (Join-Path $root 'tiers')
Remove-DirIfPresent (Join-Path $root 'tier_paths')
Remove-DirIfPresent (Join-Path $root 'tier_templates')

foreach ($domain in $domains) {
    for ($i = 0; $i -lt $tiers.Count; $i++) {
        $tier = $tiers[$i]
        $upgrade = $upgrades[$domain]
        switch ($i) {
            0 { $downgrade = 'minecraft:stick' }
            1 { $downgrade = $upgrades[$domain] }
            2 { $downgrade = $step1[$domain] }
            3 { $downgrade = $step2[$domain] }
        }
        switch ($i) {
            0 { $upgrade = $upgrades[$domain] }
            1 { $upgrade = $step1[$domain] }
            2 { $upgrade = $step2[$domain] }
            3 { $upgrade = $step3[$domain] }
        }

        $previous = ''
        if ($i -gt 0) { $previous = "  `"previous_tier`": `"tieredechoes:$($tiers[$i-1])`",`r`n" }

        $tierJson = @"
{
  "schema_version": 1,
  "id": "tieredechoes:$tier",
  "domain": "$domain",
  "display_rarity": "$($rarities[$i])",
  "sort_order": $($sorts[$i]),
  "experience_required": $($xp[$i]),
  "generated_upgrade_template": true,
  "generated_downgrade_template": true,
  "upgrade_ingredient": {
    "item": "$upgrade"
  },
  "downgrade_ingredient": {
    "item": "$downgrade"
  },
$previous  "attribute_multipliers": $($multipliers[$i]),
  "curios_set_bonuses": $($setBonuses[$i]),
  "compat_amplifiers": {}
}
"@
        Write-Utf8NoBom (Join-Path $root "tiers\$domain\$tier.json") $tierJson
    }

    $pathJson = @"
{
  "schema_version": 1,
  "id": "tieredechoes:${domain}_linear",
  "domain": "$domain",
  "edges": [
    {
      "schema_version": 1,
      "from": "tieredechoes:common",
      "to": "tieredechoes:uncommon",
      "display_branch_id": "${domain}_common_to_uncommon",
      "attunement_ingredient": {
        "item": "$($step1[$domain])"
      },
      "downgrade_to": "tieredechoes:common"
    },
    {
      "schema_version": 1,
      "from": "tieredechoes:uncommon",
      "to": "tieredechoes:rare",
      "display_branch_id": "${domain}_uncommon_to_rare",
      "attunement_ingredient": {
        "item": "$($step2[$domain])"
      },
      "downgrade_to": "tieredechoes:uncommon"
    },
    {
      "schema_version": 1,
      "from": "tieredechoes:rare",
      "to": "tieredechoes:epic",
      "display_branch_id": "${domain}_rare_to_epic",
      "attunement_ingredient": {
        "item": "$($step3[$domain])"
      },
      "downgrade_to": "tieredechoes:rare"
    }
  ]
}
"@
    Write-Utf8NoBom (Join-Path $root "tier_paths\$domain\linear.json") $pathJson

    foreach ($tier in $tiers) {
        foreach ($kind in @('upgrade', 'downgrade')) {
            $templateJson = @"
{
  "schema_version": 1,
  "id": "tieredechoes:${tier}_${domain}_${kind}_template",
  "tier": "tieredechoes:$tier",
  "domain": "$domain",
  "kind": "$kind",
  "item": "tieredechoes:${tier}_${kind}_template"
}
"@
            Write-Utf8NoBom (Join-Path $root "tier_templates\$domain\${tier}_${kind}_template.json") $templateJson
        }
    }
}

$tiersCount = (Get-ChildItem -Recurse -File (Join-Path $root 'tiers')).Count
$pathsCount = (Get-ChildItem -Recurse -File (Join-Path $root 'tier_paths')).Count
$templatesCount = (Get-ChildItem -Recurse -File (Join-Path $root 'tier_templates')).Count
Write-Output "tiers=$tiersCount paths=$pathsCount templates=$templatesCount"
