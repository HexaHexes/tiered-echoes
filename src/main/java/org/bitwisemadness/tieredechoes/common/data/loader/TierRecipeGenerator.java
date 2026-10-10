package org.bitwisemadness.tieredechoes.common.data.loader;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.recipe.TierDowngradeRecipe;
import org.bitwisemadness.tieredechoes.common.recipe.TierUpgradeRecipe;
import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the smithing recipes implied by the tier path graph and pushes them into the live
 * {@link RecipeManager}.
 *
 * <p>Recipes are derived rather than hand-authored so that a datapack author only has to describe
 * the graph once in {@code tier_paths}; every transition then becomes a smithing recipe for free,
 * and adding a branch adds its recipe automatically.</p>
 */
public final class TierRecipeGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Recipe id paths this generator owns; stripped before re-injection so reloads do not pile up. */
    static final String UPGRADE_PREFIX = "tier_upgrade/";
    static final String DOWNGRADE_PREFIX = "tier_downgrade/";

    /**
     * Item tag used as the smithing base slot per domain. These tags ship with the mod and are meant
     * to be extended by datapacks so modded gear becomes tierable.
     */
    private static final Map<TierDomain, TagKey<Item>> BASE_TAGS = new EnumMap<>(TierDomain.class);

    static {
        BASE_TAGS.put(TierDomain.WEAPON, TieredEchoesTagKeys.item("weapon_bases"));
        BASE_TAGS.put(TierDomain.TOOL, TieredEchoesTagKeys.item("tool_bases"));
        BASE_TAGS.put(TierDomain.ARMOR, TieredEchoesTagKeys.item("armor_bases"));
        BASE_TAGS.put(TierDomain.ELYTRA, TieredEchoesTagKeys.item("elytra_bases"));
        BASE_TAGS.put(TierDomain.ACCESSORY, TieredEchoesTagKeys.item("accessory_bases"));
        BASE_TAGS.put(TierDomain.STORAGE, TieredEchoesTagKeys.item("storage_bases"));
    }

    private TierRecipeGenerator() {
    }

    /** True when the id lives in the generated namespace and would collide on re-injection. */
    public static boolean isGeneratedRecipeId(final ResourceLocation id) {
        if (!TieredEchoes.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        return id.getPath().startsWith(UPGRADE_PREFIX) || id.getPath().startsWith(DOWNGRADE_PREFIX);
    }

    public static void regenerateAndInject(final RecipeManager recipeManager, final ValidationReport report) {
        if (recipeManager == null) {
            // Not a server: nothing to inject recipes into, but the rest of the data still loads.
            return;
        }
        final List<Recipe<?>> generated = new ArrayList<>();
        // Downgrades stay available even in AUTO_ONLY: a datapack that removes the upgrade path still
        // owes the player a way back down.
        final boolean publishUpgrades = PolicyService.getInstance().get().upgradeRecipesEnabled();
        for (final TierEdge edge : PathService.getInstance().getAllEdgesLike()) {
            final TierService tiers = TierService.getInstance();
            final Optional<TierDefinition> source = tiers.getTier(edge.from(), edge.domain());
            final Optional<TierDefinition> target = tiers.getTier(edge.to(), edge.domain());
            if (source.isEmpty() || target.isEmpty()) {
                // validate() already reported the dangling endpoints; skip rather than invent a recipe.
                continue;
            }
            if (publishUpgrades) {
                addUpgrade(generated, report, edge, source.get(), target.get());
            }
            edge.downgradeTo()
                    .flatMap(downgradeTo -> tiers.getTier(downgradeTo, edge.domain()))
                    .ifPresent(downgradeTarget -> addDowngrade(generated, report, edge, target.get(), downgradeTarget));
        }
        inject(recipeManager, generated);
        LOGGER.info("Injected {} generated tier transition recipes", generated.size());
    }

    private static void addUpgrade(final List<Recipe<?>> out, final ValidationReport report,
                                   final TierEdge edge, final TierDefinition source, final TierDefinition target) {
        final Ingredient template = templateItem(target, false)
                .map(Ingredient::of)
                .orElse(null);
        if (template == null) {
            report.add(missingTemplate(edge, target, false));
            return;
        }
        final Ingredient addition = additionFor(edge, target);
        if (addition.isEmpty()) {
            report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "missing_upgrade_addition",
                    "Edge has no attunement ingredient and its target tier has no upgrade ingredient; no smithing recipe was generated.",
                    Map.of("edge", edge.from() + "->" + edge.to(), "domain", edge.domain().getSerializedName())));
            return;
        }
        final Ingredient base = baseIngredient(edge.domain());
        if (base.isEmpty()) {
            report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "missing_domain_base_tag",
                    "No smithing base tag is registered for this domain; no smithing recipe was generated.",
                    Map.of("edge", edge.from() + "->" + edge.to(), "domain", edge.domain().getSerializedName())));
            return;
        }
        out.add(new TierUpgradeRecipe(
                recipeId(UPGRADE_PREFIX, edge.domain(), edge.from(), edge.to()),
                template, base, addition,
                edge.domain(), edge.from(), edge.to()));
    }

    private static void addDowngrade(final List<Recipe<?>> out, final ValidationReport report,
                                     final TierEdge edge, final TierDefinition current, final TierDefinition target) {
        // The template names the tier being left behind, which reads naturally in an inventory:
        // a "rare downgrade template" is what you spend to leave rare behind.
        final Ingredient template = templateItem(current, true)
                .map(Ingredient::of)
                .orElse(null);
        if (template == null) {
            report.add(missingTemplate(edge, current, true));
            return;
        }
        final Ingredient addition = edge.attunementIngredient()
                .or(current::downgradeIngredient)
                .map(IngredientMatcher::toIngredient)
                .orElse(Ingredient.EMPTY);
        if (addition.isEmpty()) {
            report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "missing_downgrade_addition",
                    "Neither the edge nor the source tier declares a downgrade ingredient; no smithing recipe was generated.",
                    Map.of("edge", edge.to() + "->" + edge.downgradeTo().orElse(target.id()), "domain", edge.domain().getSerializedName())));
            return;
        }
        final Ingredient base = baseIngredient(edge.domain());
        if (base.isEmpty()) {
            return;
        }
        out.add(new TierDowngradeRecipe(
                recipeId(DOWNGRADE_PREFIX, edge.domain(), edge.to(), edge.downgradeTo().orElse(edge.from())),
                template, base, addition,
                edge.domain(), edge.to(), edge.downgradeTo().orElse(edge.from())));
    }

    private static ValidationIssue missingTemplate(final TierEdge edge, final TierDefinition tier, final boolean downgrade) {
        return new ValidationIssue(ValidationIssue.Severity.WARNING, "missing_smithing_template",
                "No template item is registered for this tier; no smithing recipe was generated. Register one named "
                        + tier.id().getPath() + (downgrade ? "_downgrade_template" : "_upgrade_template") + " or set the tier's generated_*_template flag.",
                Map.of("edge", edge.from() + "->" + edge.to(), "domain", edge.domain().getSerializedName(), "tier", tier.id().toString()));
    }

    /** The edge's attunement ingredient wins; the target tier's upgrade ingredient is the fallback. */
    private static Ingredient additionFor(final TierEdge edge, final TierDefinition target) {
        final Optional<IngredientMatcher> declared = edge.attunementIngredient()
                .or(() -> Optional.ofNullable(target.upgradeIngredient()));
        return declared.map(IngredientMatcher::toIngredient).orElse(Ingredient.EMPTY);
    }

    private static Ingredient baseIngredient(final TierDomain domain) {
        final TagKey<Item> tag = BASE_TAGS.get(domain);
        return tag == null ? Ingredient.EMPTY : Ingredient.of(tag);
    }

    private static Optional<Item> templateItem(final TierDefinition tier, final boolean downgrade) {
        // Resolution goes through TemplateService so a tier_templates declaration can override which
        // item sits in the template slot, instead of forcing every datapack to register a Java item.
        return TemplateService.getInstance().templateItem(tier, downgrade);
    }

    /**
     * Names a generated recipe after the transition it performs, not after the edge that implied it.
     *
     * <p>An edge and its downgrade are the same story told in two directions, so deriving both ids
     * from the edge alone would make a recipe that steps <em>down</em> from uncommon to common claim
     * to be {@code common_to_uncommon}. Every id here is user-visible — it is what a datapack author
     * greps for, and what {@code /recipe give} takes — so it should say what the recipe does.</p>
     */
    private static ResourceLocation recipeId(final String prefix, final TierDomain domain,
                                             final ResourceLocation from, final ResourceLocation to) {
        return new ResourceLocation(TieredEchoes.MOD_ID, prefix + domain.getSerializedName()
                + "/" + from.getPath() + "_to_" + to.getPath());
    }

    /**
     * Rebuilds the manager's recipe table, dropping anything previously generated so that a
     * {@code /reload} refreshes transitions instead of accumulating stale ones.
     *
     * <p>{@link RecipeManager#replaceRecipes} throws on duplicate ids, hence the id-keyed merge
     * rather than a straight append.</p>
     */
    private static void inject(final RecipeManager recipeManager, final List<Recipe<?>> generated) {
        final Map<ResourceLocation, Recipe<?>> merged = new LinkedHashMap<>();
        for (final Recipe<?> recipe : recipeManager.getRecipes()) {
            if (isGeneratedRecipeId(recipe.getId())) {
                continue;
            }
            merged.put(recipe.getId(), recipe);
        }
        for (final Recipe<?> recipe : generated) {
            final Recipe<?> shadowed = merged.put(recipe.getId(), recipe);
            if (shadowed != null) {
                LOGGER.warn("Generated tier recipe {} replaced a recipe of the same id", recipe.getId());
            }
        }
        recipeManager.replaceRecipes(merged.values());
    }
}
