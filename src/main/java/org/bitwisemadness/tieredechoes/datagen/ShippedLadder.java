package org.bitwisemadness.tieredechoes.datagen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
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
    private static final List<String> TIER_NAMES = List.of("common", "uncommon", "rare", "epic");
    private static final List<Rarity> RARITIES = List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC);
    private static final List<Integer> EXPERIENCE = List.of(0, 25, 75, 150);

    /**
     * Ingredients for weapon, tool and armour ladders. Accessory gear escalates through rarer
     * materials because accessories are rarer themselves and have no durability to trade away.
     */
    private static final List<String> STANDARD_INGREDIENTS = List.of(
            "minecraft:cobblestone", "minecraft:iron_ingot", "minecraft:diamond", "minecraft:netherite_ingot");
    private static final List<String> ACCESSORY_INGREDIENTS = List.of(
            "minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond", "minecraft:nether_star");

    /** What a player spends to craft a second copy of a tier's templates. */
    private static final List<String> TEMPLATE_CRAFTING_INGREDIENTS = STANDARD_INGREDIENTS;

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
                    "thermal_resistance", 1.4D));

    /**
     * Multipliers for domains whose items carry no attributes at all in vanilla — a shield, an elytra
     * and a bow all scale durability and nothing else. Declaring {@code attack_damage} on a bow tier
     * would do nothing, because no such modifier is ever applied to it, and a datapack author reading
     * the file would reasonably conclude that it was.
     */
    private static final List<Map<String, Double>> DURABILITY_ONLY_MULTIPLIERS = List.of(
            map("durability", 1.0D),
            map("durability", 1.1D),
            map("durability", 1.2D),
            map("durability", 1.35D));

    private static final List<Map<String, Double>> SET_BONUSES = List.of(
            map("*2", 1.05D),
            map("*2", 1.07D, "*3", 1.1D),
            map("*2", 1.1D, "*3", 1.15D, "*4", 1.2D),
            map("*2", 1.12D, "*3", 1.18D, "*4", 1.24D));

    /** Domains with no attribute surface, so their tiers declare durability scaling only. */
    private static boolean isDurabilityOnly(final TierDomain domain) {
        return switch (domain) {
            case SHIELD, RANGED_WEAPONS, ELYTRA -> true;
            default -> false;
        };
    }

    /**
     * Curios set bonuses only mean anything for items that live in a curios slot. Emitting them on a
     * shield tier would advertise a feature those items cannot use.
     */
    private static Map<String, Double> setBonusesFor(final TierDomain domain, final int index) {
        return isDurabilityOnly(domain) ? Map.of() : SET_BONUSES.get(index);
    }

    private ShippedLadder() {
    }

    /** The domains that ship a ladder. Every {@code TierDomain} is one; there are no compat-only ones. */
    public static List<TierDomain> domains() {
        return TierDomain.builtinDomains();
    }

    public static List<String> tierNames() {
        return TIER_NAMES;
    }

    /** One upgrade and one downgrade template per tier, keyed by tier alone. */
    public static List<String> templateKinds() {
        return List.of("upgrade", "downgrade");
    }

    public static List<TierDefinition> tiers() {
        final List<TierDefinition> tiers = new ArrayList<>();
        for (final TierDomain domain : domains()) {
            final List<String> ingredients = ingredientsFor(domain);
            for (int index = 0; index < TIER_NAMES.size(); index++) {
                tiers.add(tier(domain, ingredients, index));
            }
        }
        return tiers;
    }

    public static List<TierEdge> edges() {
        final List<TierEdge> edges = new ArrayList<>();
        for (final TierDomain domain : domains()) {
            final List<String> ingredients = ingredientsFor(domain);
            // Each edge steps one position up the ladder and points its downgrade back at the tier
            // it came from, so the way down is always the mirror of the way up.
            for (int index = 1; index < TIER_NAMES.size(); index++) {
                edges.add(edge(domain, ingredients, index));
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
                        "downgrade".equals(kind),
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

    private static TierDefinition tier(final TierDomain domain, final List<String> ingredients, final int index) {
        final String name = TIER_NAMES.get(index);
        // A tier's downgrade ingredient is the material the tier below it was made from, so stepping
        // down refunds the difference rather than charging again for the same thing.
        final String downgrade = index == 0 ? "minecraft:stick" : ingredients.get(index - 1);
        final List<Map<String, Double>> multipliers =
                isDurabilityOnly(domain) ? DURABILITY_ONLY_MULTIPLIERS : ATTRIBUTE_MULTIPLIERS;
        return new TierDefinition(
                new ResourceLocation(TieredEchoes.MOD_ID, name),
                domain,
                1,
                RARITIES.get(index),
                index,
                EXPERIENCE.get(index),
                item(ingredients.get(index)),
                Optional.of(item(downgrade)),
                index == 0 ? Optional.empty() : Optional.of(new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index - 1))),
                Optional.of(TieredEchoes.MOD_ID),
                multipliers.get(index),
                setBonusesFor(domain, index),
                Map.of(),
                true,
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static TierEdge edge(final TierDomain domain, final List<String> ingredients, final int index) {
        final ResourceLocation from = new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index - 1));
        final ResourceLocation to = new ResourceLocation(TieredEchoes.MOD_ID, TIER_NAMES.get(index));
        return new TierEdge(
                from,
                to,
                domain,
                index - 1,
                Optional.of(item(ingredients.get(index))),
                Optional.of(from),
                Optional.of(TieredEchoes.MOD_ID),
                Optional.empty(),
                Optional.empty(),
                Optional.of(domain.getSerializedName() + "_" + from.getPath() + "_to_" + to.getPath()),
                Map.of());
    }

    /**
     * The upgrade ingredients a domain walks up.
     *
     * <p>{@code ACCESSORY} and {@code ELYTRA} escalate through rarer materials because they are rare
     * themselves and have no durability to trade away — an elytra's ladder is a rarity ladder, and
     * making a player pay netherite to improve a flight item would be a materials joke rather than a
     * progression step.</p>
     */
    private static List<String> ingredientsFor(final TierDomain domain) {
        return switch (domain) {
            case ACCESSORY, ELYTRA -> ACCESSORY_INGREDIENTS;
            default -> STANDARD_INGREDIENTS;
        };
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
