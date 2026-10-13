package org.bitwisemadness.tieredechoes.datagen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ladder that ships inside the mod jar, as the single place it is described.
 *
 * <p>This class exists only so that {@link TieredEchoesDataProvider} can write the JSON the game
 * actually loads. The runtime never reads it: {@code TieredEchoesDataReloader} seeds from nothing
 * and takes whatever the datapack contributes, which means the generated files in
 * {@code src/main/resources} <em>are</em> the built-in ladder rather than a copy of one held in
 * Java. A datapack that omits a tier therefore removes it, instead of the Java fallback quietly
 * restoring it.</p>
 *
 * <p>That also makes drift a real risk — edit a number here, forget {@code runData}, and the jar
 * ships last week's ladder. {@code ShippedLadderTest} fails the build when the committed files and
 * this class disagree, which is why {@code runData} is a manual step rather than part of
 * {@code build}.</p>
 */
public final class ShippedLadder {

    /** Tier order along the ladder. Index position is what pairs the parallel tables below. */
    private static final List<String> TIER_NAMES = List.of("common", "uncommon", "rare", "epic", "legendary");

    /**
     * Rarity per rung, as names rather than constants.
     *
     * <p>{@code LEGENDARY} is added at runtime by {@link TERarities} and so does not exist as a
     * source-level constant; spelling the table as strings keeps this class compilable and lets the
     * resolution happen where the mod is guaranteed to be loaded.</p>
     */
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", TERarities.LEGENDARY);

    /**
     * The material each step up costs, one per rung.
     *
     * <p>Copper opens the ladder and netherite closes it. The escalation is the familiar vanilla
     * progression read in the order a player actually meets the metals, so the smithing table
     * teaches the cost curve without a tooltip.</p>
     */
    private static final List<String> UPGRADE_INGREDIENTS = List.of(
            "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot",
            "minecraft:diamond", "minecraft:netherite_ingot");

    /** What a player spends to craft a second copy of a tier's upgrade template. */
    private static final List<String> TEMPLATE_CRAFTING_INGREDIENTS = UPGRADE_INGREDIENTS;

    private static final List<Map<String, Double>> ATTRIBUTE_MULTIPLIERS = List.of(
            map("durability", 1.0D, "attack_damage", 1.0D, "attack_speed", 1.0D, "armor", 1.0D,
                    "armor_toughness", 1.0D, "knockback_resistance", 1.0D, "max_mana", 1.0D,
                    "mana_regen", 1.0D, "cold_resistance", 1.0D, "heat_resistance", 1.0D,
                    "thermal_resistance", 1.0D),
            map("durability", 1.1D, "attack_damage", 1.12D, "attack_speed", 1.04D, "armor", 1.12D,
                    "armor_toughness", 1.1D, "knockback_resistance", 1.08D, "max_mana", 1.12D,
                    "mana_regen", 1.08D, "cold_resistance", 1.12D, "heat_resistance", 1.12D,
                    "thermal_resistance", 1.12D),
            map("durability", 1.2D, "attack_damage", 1.25D, "attack_speed", 1.08D, "armor", 1.25D,
                    "armor_toughness", 1.2D, "knockback_resistance", 1.15D, "max_mana", 1.25D,
                    "mana_regen", 1.15D, "cold_resistance", 1.25D, "heat_resistance", 1.25D,
                    "thermal_resistance", 1.25D),
            map("durability", 1.35D, "attack_damage", 1.4D, "attack_speed", 1.12D, "armor", 1.4D,
                    "armor_toughness", 1.35D, "knockback_resistance", 1.25D, "max_mana", 1.4D,
                    "mana_regen", 1.25D, "cold_resistance", 1.4D, "heat_resistance", 1.4D,
                    "thermal_resistance", 1.4D),
            map("durability", 1.55D, "attack_damage", 1.6D, "attack_speed", 1.17D, "armor", 1.6D,
                    "armor_toughness", 1.55D, "knockback_resistance", 1.4D, "max_mana", 1.6D,
                    "mana_regen", 1.4D, "cold_resistance", 1.6D, "heat_resistance", 1.6D,
                    "thermal_resistance", 1.6D));

    private ShippedLadder() {
    }

    /**
     * The domains that ship a ladder.
     *
     * <p>{@code TierDomain} still holds shield, ranged, elytra and accessory, and their tag mappings
     * still resolve, so a datapack can declare a ladder for any of them. What is absent is the JSON
     * for a built-in one: those items scale durability and nothing else in vanilla, so a shipped
     * ladder would be a durability table wearing a different name, and a pack that wants to give a
     * modded bow real attributes is better served writing the contract it actually means.</p>
     */
    public static List<TierDomain> domains() {
        return TierDomain.builtinDomains();
    }

    public static List<String> tierNames() {
        return TIER_NAMES;
    }

    /**
     * Templates the mod ships.
     *
     * <p>Upgrade only. Downgrades are disabled pending a rework of how the way down should be
     * expressed, so generating downgrade templates would put items in the creative tab and recipes
     * in the book for a transition the smithing table will not perform.</p>
     */
    public static List<String> templateKinds() {
        return List.of("upgrade");
    }

    public static List<TierDefinition> tiers() {
        final List<TierDefinition> tiers = new ArrayList<>();
        for (final TierDomain domain : domains()) {
            for (int index = 0; index < TIER_NAMES.size(); index++) {
                tiers.add(tier(domain, index));
            }
        }
        return tiers;
    }

    public static List<TierEdge> edges() {
        final List<TierEdge> edges = new ArrayList<>();
        for (final TierDomain domain : domains()) {
            // Each edge steps one position up the ladder. There is no edge into the first rung,
            // because there is no rung below it: an untiered item joins the ladder through the
            // bootstrap transition rather than through the path graph.
            for (int index = 1; index < TIER_NAMES.size(); index++) {
                edges.add(edge(domain, index));
            }
        }
        return edges;
    }

    public static List<TemplateDefinition> templates() {
        final List<TemplateDefinition> templates = new ArrayList<>();
        for (final String tier : TIER_NAMES) {
            for (final String kind : templateKinds()) {
                final ResourceLocation id = new ResourceLocation(TieredEchoes.MOD_ID, tier + "_" + kind + "_template");
                templates.add(new TemplateDefinition(
                        id,
                        new ResourceLocation(TieredEchoes.MOD_ID, tier),
                        false,
                        Optional.of(id),
                        Optional.empty(),
                        Optional.empty()));
            }
        }
        return templates;
    }

    /**
     * The crafting recipes that let a player make a second template. Templates are consumed by the
     * smithing table, so without these a tier is a one-way trip.
     */
    public static List<TemplateCraftingRecipe> templateRecipes() {
        final List<TemplateCraftingRecipe> recipes = new ArrayList<>();
        for (int index = 0; index < TIER_NAMES.size(); index++) {
            final String tier = TIER_NAMES.get(index);
            for (final String kind : templateKinds()) {
                final String name = tier + "_" + kind + "_template";
                recipes.add(new TemplateCraftingRecipe(
                        new ResourceLocation(TieredEchoes.MOD_ID, "templates/" + name + "_copy"),
                        new ResourceLocation(TieredEchoes.MOD_ID, name),
                        new ResourceLocation(TEMPLATE_CRAFTING_INGREDIENTS.get(index))));
            }
        }
        return recipes;
    }

    /** The ingredient the template recipe takes in its centre column, one step up per tier. */
    public static List<String> templateCraftingIngredients() {
        return TEMPLATE_CRAFTING_INGREDIENTS;
    }

    /**
     * The rarity a rung declares, as the name written to {@code display_rarity}.
     *
     * <p>Read from the table rather than from the resolved {@link Rarity} for a specific reason:
     * {@code LEGENDARY} only exists once Forge has extended the enum, which happens in a game boot
     * and not in a unit-test JVM. Calling {@code rarity.name()} would therefore yield
     * {@code LEGENDARY} from {@code runData} and {@code COMMON} from the drift guard, and the two
     * would fight over the same committed file. A generated artifact's content must not depend on
     * whether a class transformer happened to run.</p>
     */
    public static String rarityNameFor(final ResourceLocation tierId) {
        final int index = TIER_NAMES.indexOf(tierId.getPath());
        return index < 0 ? Rarity.COMMON.name() : RARITIES.get(index);
    }

    private static TierDefinition tier(final TierDomain domain, final int index) {
        final String name = TIER_NAMES.get(index);
        // No downgrade ingredient: the way down is disabled, and declaring a material for a
        // transition that cannot be taken would advertise a refund the table will not honour.
        return new TierDefinition(
                new ResourceLocation(TieredEchoes.MOD_ID, name),
                domain,
                1,
                rarity(index),
                index,
                item(UPGRADE_INGREDIENTS.get(index)),
                Optional.empty(),
                index == 0 ? Optional.empty() : Optional.of(new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index - 1))),
                Optional.of(TieredEchoes.MOD_ID),
                ATTRIBUTE_MULTIPLIERS.get(index),
                Map.of(),
                Map.of(),
                true,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static TierEdge edge(final TierDomain domain, final int index) {
        final ResourceLocation from = new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index - 1));
        final ResourceLocation to = new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index));
        return new TierEdge(
                from,
                to,
                domain,
                index - 1,
                Optional.of(item(UPGRADE_INGREDIENTS.get(index))),
                // No downgrade target, for the same reason the tier declares no downgrade material.
                Optional.empty(),
                Optional.of(TieredEchoes.MOD_ID),
                Optional.of(domain.getSerializedName() + "_" + from.getPath() + "_to_" + to.getPath()),
                Map.of());
    }

    private static Rarity rarity(final int index) {
        return TERarities.byName(RARITIES.get(index), Rarity.COMMON);
    }

    private static IngredientMatcher item(final String id) {
        final ResourceLocation parsed = new ResourceLocation(id);
        return new IngredientMatcher(IngredientMatcher.Kind.ITEM, parsed);
    }

    /** Builds an insertion-ordered map, because the JSON output is compared byte for byte. */
    private static Map<String, Double> map(final Object... keysAndValues) {
        final Map<String, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            result.put((String) keysAndValues[index], (Double) keysAndValues[index + 1]);
        }
        return result;
    }

    /** One generated crafting recipe: a template plus tier-scaled materials, yielding two copies. */
    public record TemplateCraftingRecipe(ResourceLocation id, ResourceLocation template, ResourceLocation ingredient) {
    }
}
