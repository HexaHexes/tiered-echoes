package org.bitwisemadness.tieredechoes.common.datagen.packs.support;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractGeneratedPackProvider implements DataProvider {
    protected final PackOutput output;
    private final String packId;
    private final String description;

    protected AbstractGeneratedPackProvider(final PackOutput output, final String packId, final String description) {
        this.output = output;
        this.packId = packId;
        this.description = description;
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cachedOutput) {
        final Path root = this.output.getOutputFolder();
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(DataProvider.saveStable(cachedOutput, this.createPackMeta(), root.resolve(this.packId + "/pack.mcmeta")));
        futures.add(this.writeDefaultTierSet(cachedOutput, root));
        futures.add(this.writeDefaultPathSet(cachedOutput, root));
        futures.add(this.writeTemplateSet(cachedOutput, root));
        futures.add(this.writeRecipeSet(cachedOutput, root));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeDefaultTierSet(final CachedOutput cachedOutput, final Path root) {
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (final String domain : this.defaultDomains()) {
            futures.add(writeTier(cachedOutput, root, domain, "common", Rarity.COMMON, 0, 0, domain.equals("accessory") ? "minecraft:gold_ingot" : "minecraft:cobblestone", null));
            futures.add(writeTier(cachedOutput, root, domain, "uncommon", Rarity.UNCOMMON, 1, 25, domain.equals("accessory") ? "minecraft:emerald" : "minecraft:iron_ingot", "tieredechoes:common"));
            futures.add(writeTier(cachedOutput, root, domain, "rare", Rarity.RARE, 2, 75, "minecraft:diamond", "tieredechoes:uncommon"));
            futures.add(writeTier(cachedOutput, root, domain, "epic", Rarity.EPIC, 3, 150, domain.equals("accessory") ? "minecraft:nether_star" : "minecraft:netherite_ingot", "tieredechoes:rare"));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeDefaultPathSet(final CachedOutput cachedOutput, final Path root) {
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (final String domain : this.defaultDomains()) {
            final JsonObject path = new JsonObject();
            path.addProperty("schema_version", 1);
            path.addProperty("id", "tieredechoes:" + domain + "_linear");
            path.addProperty("domain", domain);
            final JsonArray edges = new JsonArray();
            edges.add(edge(domain, "tieredechoes:common", "tieredechoes:uncommon", domain.equals("accessory") ? "minecraft:emerald" : "minecraft:iron_ingot", "tieredechoes:common"));
            edges.add(edge(domain, "tieredechoes:uncommon", "tieredechoes:rare", "minecraft:diamond", "tieredechoes:uncommon"));
            edges.add(edge(domain, "tieredechoes:rare", "tieredechoes:epic", domain.equals("accessory") ? "minecraft:nether_star" : "minecraft:netherite_ingot", "tieredechoes:rare"));
            path.add("edges", edges);
            futures.add(DataProvider.saveStable(cachedOutput, path, root.resolve(this.packId + "/data/" + this.packId + "/dba_tier_paths/" + domain + "/linear.json")));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeTemplateSet(final CachedOutput cachedOutput, final Path root) {
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (final String domain : this.defaultDomains()) {
            for (final String tier : new String[] {"common", "uncommon", "rare", "epic"}) {
                final JsonObject upgradeTemplate = new JsonObject();
                upgradeTemplate.addProperty("schema_version", 1);
                upgradeTemplate.addProperty("id", "tieredechoes:" + tier + "_" + domain + "_upgrade_template");
                upgradeTemplate.addProperty("tier", "tieredechoes:" + tier);
                upgradeTemplate.addProperty("domain", domain);
                upgradeTemplate.addProperty("kind", "upgrade");
                futures.add(DataProvider.saveStable(cachedOutput, upgradeTemplate, root.resolve(this.packId + "/data/" + this.packId + "/dba_upgrade_templates/" + domain + "/" + tier + "_upgrade_template.json")));

                final JsonObject downgradeTemplate = new JsonObject();
                downgradeTemplate.addProperty("schema_version", 1);
                downgradeTemplate.addProperty("id", "tieredechoes:" + tier + "_" + domain + "_downgrade_template");
                downgradeTemplate.addProperty("tier", "tieredechoes:" + tier);
                downgradeTemplate.addProperty("domain", domain);
                downgradeTemplate.addProperty("kind", "downgrade");
                futures.add(DataProvider.saveStable(cachedOutput, downgradeTemplate, root.resolve(this.packId + "/data/" + this.packId + "/dba_upgrade_templates/" + domain + "/" + tier + "_downgrade_template.json")));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeRecipeSet(final CachedOutput cachedOutput, final Path root) {
        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (final String domain : this.defaultDomains()) {
            for (final String tier : new String[] {"uncommon", "rare", "epic"}) {
                final JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "tieredechoes:upgrade_placeholder");
                recipe.addProperty("schema_version", 1);
                recipe.addProperty("tier", "tieredechoes:" + tier);
                recipe.addProperty("domain", domain);
                recipe.addProperty("disabled_by_xp_only", false);
                futures.add(DataProvider.saveStable(cachedOutput, recipe, root.resolve(this.packId + "/data/" + this.packId + "/recipes/" + domain + "/" + tier + "_upgrade.json")));

                final JsonObject downgradeRecipe = new JsonObject();
                downgradeRecipe.addProperty("type", "tieredechoes:downgrade_placeholder");
                downgradeRecipe.addProperty("schema_version", 1);
                downgradeRecipe.addProperty("tier", "tieredechoes:" + tier);
                downgradeRecipe.addProperty("domain", domain);
                futures.add(DataProvider.saveStable(cachedOutput, downgradeRecipe, root.resolve(this.packId + "/data/" + this.packId + "/recipes/" + domain + "/" + tier + "_downgrade.json")));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> writeTier(final CachedOutput cachedOutput, final Path root, final String domain, final String tierId, final Rarity rarity, final int sortOrder, final int xp, final String ingredient, final String previousTier) {
        final JsonObject json = new JsonObject();
        json.addProperty("schema_version", 1);
        json.addProperty("id", "tieredechoes:" + tierId);
        json.addProperty("domain", domain);
        json.addProperty("display_rarity", rarity.name());
        json.addProperty("sort_order", sortOrder);
        json.addProperty("experience_required", xp);
        json.addProperty("generated_upgrade_template", true);
        json.addProperty("generated_downgrade_template", true);
        final JsonObject upgradeIngredient = new JsonObject();
        upgradeIngredient.addProperty("item", ingredient);
        json.add("upgrade_ingredient", upgradeIngredient);
        if (previousTier != null) {
            json.addProperty("previous_tier", previousTier);
        }
        return DataProvider.saveStable(cachedOutput, json, root.resolve(this.packId + "/data/" + this.packId + "/dba_tier_definitions/" + domain + "/" + tierId + ".json"));
    }

    private JsonObject createPackMeta() {
        final JsonObject root = new JsonObject();
        final JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", this.description);
        root.add("pack", pack);
        if (this.requiredMods().length > 0) {
            final JsonObject te = new JsonObject();
            final JsonArray mods = new JsonArray();
            for (final String mod : this.requiredMods()) {
                mods.add(mod);
            }
            te.add("requires_mods", mods);
            root.add("tieredechoes", te);
        }
        return root;
    }

    private JsonObject edge(final String domain, final String from, final String to, final String ingredientItem, final String downgradeTo) {
        final JsonObject edge = new JsonObject();
        edge.addProperty("schema_version", 1);
        edge.addProperty("from", from);
        edge.addProperty("to", to);
        edge.addProperty("display_branch_id", domain + "_" + from.substring(from.indexOf(':') + 1) + "_to_" + to.substring(to.indexOf(':') + 1));
        final JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", ingredientItem);
        edge.add("attunement_ingredient", ingredient);
        edge.addProperty("downgrade_to", downgradeTo);
        return edge;
    }

    protected String[] defaultDomains() {
        return new String[] {"weapon", "tool", "armor", "elytra", "accessory", "storage"};
    }

    protected String[] requiredMods() {
        return new String[0];
    }

    @Override
    public String getName() {
        return "Tiered Echoes Generated Pack: " + this.packId;
    }
}
