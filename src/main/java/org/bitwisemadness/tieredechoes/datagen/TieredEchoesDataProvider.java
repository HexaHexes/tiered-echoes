package org.bitwisemadness.tieredechoes.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Writes the built-in datapack into {@code src/generated/resources}, where it is committed and wired
 * into the jar as an extra resources source directory.
 *
 * <p>The output must not be {@code src/main/resources}. A Forge data run owns its output directory
 * and wipes whatever it did not just generate, which would take the hand-authored tags, GameTest
 * structures, assets and {@code mods.toml} with it on every run. Keeping the two directories apart
 * means datagen can only ever damage its own output.</p>
 *
 * <p>{@code ShippedLadderTest} fails the build if the committed output stops matching
 * {@link ShippedLadder}, which is what makes running this a manual step viable.</p>
 */
public final class TieredEchoesDataProvider implements DataProvider {

    private static final String TIER_DIRECTORY = "tiers";
    private static final String PATH_DIRECTORY = "tier_paths";
    private static final String TEMPLATE_DIRECTORY = "tier_templates";
    private static final String RECIPE_DIRECTORY = "recipes";

    private final PackOutput.PathProvider tiers;
    private final PackOutput.PathProvider paths;
    private final PackOutput.PathProvider templates;
    private final PackOutput.PathProvider recipes;
    private final Path dataRoot;

    public TieredEchoesDataProvider(final PackOutput output) {
        this.tiers = output.createPathProvider(PackOutput.Target.DATA_PACK, TIER_DIRECTORY);
        this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, PATH_DIRECTORY);
        this.templates = output.createPathProvider(PackOutput.Target.DATA_PACK, TEMPLATE_DIRECTORY);
        this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, RECIPE_DIRECTORY);
        this.dataRoot = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(TieredEchoes.MOD_ID);
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        // Clear what this provider owns before writing, so deleting a tier from ShippedLadder
        // deletes its file too. A provider that only ever adds would leave the stale JSON behind
        // forever, and the loader would keep loading it.
        //
        // Only the generated directories, though: the tags and GameTest structures are hand-authored
        // and live in src/main/resources, so nothing here can reach them.
        for (final String directory : List.of(TIER_DIRECTORY, PATH_DIRECTORY, TEMPLATE_DIRECTORY)) {
            deleteRecursively(this.dataRoot.resolve(directory));
        }
        deleteRecursively(this.dataRoot.resolve(RECIPE_DIRECTORY).resolve("templates"));

        final List<CompletableFuture<?>> pending = new ArrayList<>();
        for (final TierDefinition tier : ShippedLadder.tiers()) {
            // A tier's id is domain-scoped at lookup time, not in the id itself: tieredechoes:common
            // exists once per domain. The path therefore has to carry the domain, or every domain
            // writes to tiers/common.json and only the last one survives.
            final ResourceLocation path = new ResourceLocation(TieredEchoes.MOD_ID,
                    tier.domain().getSerializedName() + "/" + tier.id().getPath());
            pending.add(save(cache, this.tiers.json(path), tierJson(tier)));
        }
        for (final TierDomain domain : ShippedLadder.domains()) {
            final List<TierEdge> edges = ShippedLadder.edges().stream()
                    .filter(edge -> edge.domain() == domain)
                    .toList();
            pending.add(save(cache,
                    this.paths.json(new ResourceLocation(TieredEchoes.MOD_ID, domain.getSerializedName() + "/linear")),
                    pathJson(domain, edges)));
        }
        for (final TemplateDefinition template : ShippedLadder.templates()) {
            pending.add(save(cache, this.templates.json(template.id()), templateJson(template)));
        }
        for (final ShippedLadder.TemplateCraftingRecipe recipe : ShippedLadder.templateRecipes()) {
            pending.add(save(cache, this.recipes.json(recipe.id()), recipeJson(recipe)));
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    /** Field order matches what {@code TieredEchoesDataReloader} reads, so the diff stays honest. */
    private static JsonObject tierJson(final TierDefinition tier) {
        final JsonObject json = new JsonObject();
        json.addProperty("schema_version", tier.schemaVersion());
        json.addProperty("id", tier.id().toString());
        json.addProperty("domain", tier.domain().getSerializedName());
        json.addProperty("display_rarity", ShippedLadder.rarityNameFor(tier.id()));
        json.addProperty("sort_order", tier.sortOrder());
        json.addProperty("generated_upgrade_template", tier.generatedUpgradeTemplate());
        json.addProperty("generated_downgrade_template", tier.generatedDowngradeTemplate());
        json.add("upgrade_ingredient", ingredientJson(tier.upgradeIngredient()));
        tier.downgradeIngredient().ifPresent(ingredient -> json.add("downgrade_ingredient", ingredientJson(ingredient)));
        tier.previousTier().ifPresent(previous -> json.addProperty("previous_tier", previous.toString()));
        json.add("attribute_multipliers", numberMapJson(tier.attributeMultipliers()));
        json.add("curios_set_bonuses", numberMapJson(tier.curiosSetBonuses()));
        json.add("compat_amplifiers", numberMapJson(tier.compatAmplifiers()));
        return json;
    }

    private static JsonObject pathJson(final TierDomain domain, final List<TierEdge> edges) {
        final JsonObject json = new JsonObject();
        json.addProperty("schema_version", 1);
        json.addProperty("id", TieredEchoes.MOD_ID + ":" + domain.getSerializedName() + "_linear");
        json.addProperty("domain", domain.getSerializedName());
        final JsonArray array = new JsonArray();
        for (final TierEdge edge : edges) {
            final JsonObject edgeJson = new JsonObject();
            edgeJson.addProperty("schema_version", 1);
            edgeJson.addProperty("from", edge.from().toString());
            edgeJson.addProperty("to", edge.to().toString());
            edge.displayBranchId().ifPresent(branch -> edgeJson.addProperty("display_branch_id", branch));
            edge.additionIngredient().ifPresent(ingredient -> edgeJson.add("addition", ingredientJson(ingredient)));
            edge.downgradeTo().ifPresent(target -> edgeJson.addProperty("downgrade_to", target.toString()));
            array.add(edgeJson);
        }
        json.add("edges", array);
        return json;
    }

    private static JsonObject templateJson(final TemplateDefinition template) {
        final JsonObject json = new JsonObject();
        json.addProperty("schema_version", 1);
        json.addProperty("id", template.id().toString());
        json.addProperty("tier", template.tier().toString());
        json.addProperty("kind", template.downgrade() ? "downgrade" : "upgrade");
        template.item().ifPresent(item -> json.addProperty("item", item.toString()));
        // Model and texture overrides are optional and the shipped templates declare none; the keys
        // are written so a datapack author can see what is available by copying a file.
        final String suffix = template.downgrade() ? "downgrade_template" : "upgrade_template";
        template.itemModelOverride().ifPresent(value -> json.addProperty(suffix + "_item_model", value.toString()));
        template.textureOverride().ifPresent(value -> json.addProperty(suffix + "_texture", value.toString()));
        return json;
    }

    private static JsonObject recipeJson(final ShippedLadder.TemplateCraftingRecipe recipe) {
        final JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        final JsonArray pattern = new JsonArray();
        pattern.add("#S#");
        pattern.add("#C#");
        pattern.add("###");
        json.add("pattern", pattern);
        final JsonObject key = new JsonObject();
        key.add("#", ingredientJson(new IngredientMatcher(IngredientMatcher.Kind.ITEM,
                new ResourceLocation("minecraft", "diamond"))));
        key.add("S", ingredientJson(new IngredientMatcher(IngredientMatcher.Kind.ITEM, recipe.template())));
        key.add("C", ingredientJson(new IngredientMatcher(IngredientMatcher.Kind.ITEM, recipe.ingredient())));
        json.add("key", key);
        final JsonObject result = new JsonObject();
        result.addProperty("item", recipe.template().toString());
        result.addProperty("count", 2);
        json.add("result", result);
        return json;
    }

    private static JsonObject ingredientJson(final IngredientMatcher matcher) {
        final JsonObject json = new JsonObject();
        json.addProperty(matcher.kind() == IngredientMatcher.Kind.ITEM ? "item" : "tag", matcher.id().toString());
        return json;
    }

    /**
     * Keys are sorted explicitly rather than following the map's iteration order. {@code
     * TierDefinition} stores these via {@code Map.copyOf}, whose order is unspecified — without
     * sorting, two JVM runs would emit the same data with the keys shuffled, and the drift test
     * would fail for no reason.
     */
    private static JsonElement numberMapJson(final Map<String, Double> values) {
        final JsonObject json = new JsonObject();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
        return json;
    }

    /** Defers to vanilla so the on-disk formatting matches every other generated datapack. */
    private static CompletableFuture<?> save(final CachedOutput cache, final Path path, final JsonObject json) {
        return DataProvider.saveStable(cache, json, path);
    }

    /** Clears a previously generated directory so a removed tier really does lose its file. */
    private static void deleteRecursively(final Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            // Deepest first, so a directory is only removed once its contents are gone.
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException exception) {
                    throw new RuntimeException("Could not delete stale generated file " + path, exception);
                }
            });
        } catch (final IOException exception) {
            throw new RuntimeException("Could not clear the generated directory " + directory, exception);
        }
    }

    @Override
    public String getName() {
        return "Tiered Echoes built-in datapack";
    }
}
