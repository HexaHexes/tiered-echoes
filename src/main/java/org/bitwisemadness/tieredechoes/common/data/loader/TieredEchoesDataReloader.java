package org.bitwisemadness.tieredechoes.common.data.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.compat.apotheosis.ApotheosisCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.arsnouveau.ArsNouveauCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.ironsspells.IronsSpellsCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.legendarysurvivaloverhaul.LegendarySurvivalOverhaulCompatBridge;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReporter;
import org.bitwisemadness.tieredechoes.common.path.DisplayBranchResolver;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TieredEchoesDataReloader implements PreparableReloadListener {
    @Override
    public CompletableFuture<Void> reload(final PreparationBarrier stage, final ResourceManager resourceManager, final ProfilerFiller preparationsProfiler, final ProfilerFiller reloadProfiler, final Executor backgroundExecutor, final Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resourceManager), backgroundExecutor)
                .thenCompose(stage::wait)
                .thenAcceptAsync(this::apply, gameExecutor);
    }

    private static LoadedData load(final ResourceManager manager) {
        final List<TierDefinition> tiers = new ArrayList<>(TierService.getInstance().getAllTiers());
        final List<TierEdge> edges = new ArrayList<>(PathService.getInstance().getAllEdgesLike());
        final ValidationReport report = new ValidationReport();
        tiers.addAll(loadTiers(manager, report));
        edges.addAll(loadEdges(manager, report));
        return new LoadedData(tiers, edges, report);
    }

    private void apply(final LoadedData data) {
        if (!data.tiers.isEmpty()) {
            TierService.getInstance().replaceAll(deduplicateTiers(data.tiers));
        }
        if (!data.edges.isEmpty()) {
            PathService.getInstance().replaceAll(data.edges);
        }
        final ValidationReport combined = data.report;
        PathService.getInstance().validate(TierService.getInstance()).issues().forEach(combined::add);
        ValidationReporter.log(combined);
    }

    private static List<TierDefinition> deduplicateTiers(final List<TierDefinition> tiers) {
        final LinkedHashMap<String, TierDefinition> map = new LinkedHashMap<>();
        for (final TierDefinition tier : tiers) {
            map.put(tier.domain().getSerializedName() + ":" + tier.id(), tier);
        }
        return List.copyOf(map.values());
    }

    private static Collection<TierDefinition> loadTiers(final ResourceManager manager, final ValidationReport report) {
        final List<TierDefinition> tiers = new ArrayList<>();
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources("dba_tier_definitions", path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                tiers.add(parseTier(entry.getKey(), json));
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "tier_parse_failure", "Failed to parse tier definition", Map.of("resource", entry.getKey().toString(), "error", exception.getMessage())));
            }
        }
        return tiers;
    }

    private static Collection<TierEdge> loadEdges(final ResourceManager manager, final ValidationReport report) {
        final List<TierEdge> edges = new ArrayList<>();
        int order = 10_000;
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources("dba_tier_paths", path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                final JsonArray jsonEdges = GsonHelper.getAsJsonArray(json, "edges", new JsonArray());
                for (final JsonElement element : jsonEdges) {
                    final JsonObject edge = element.getAsJsonObject();
                    edges.add(parseEdge(edge, order++, entry.getKey().toString()));
                }
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "path_parse_failure", "Failed to parse tier path", Map.of("resource", entry.getKey().toString(), "error", exception.getMessage())));
            }
        }
        return edges;
    }

    private static boolean isCompatEligible(final JsonObject json) {
        if (!json.has("requires_mods")) {
            return true;
        }
        final JsonArray mods = GsonHelper.getAsJsonArray(json, "requires_mods");
        for (final JsonElement modElement : mods) {
            final String mod = modElement.getAsString();
            if (!switch (mod) {
                case "apotheosis" -> ApotheosisCompatBridge.getInstance().isLoaded();
                case "ars_nouveau" -> ArsNouveauCompatBridge.getInstance().isLoaded();
                case "irons_spellbooks" -> IronsSpellsCompatBridge.getInstance().isLoaded();
                case "legendarysurvivaloverhaul" -> LegendarySurvivalOverhaulCompatBridge.getInstance().isLoaded();
                default -> net.minecraftforge.fml.ModList.get().isLoaded(mod);
            }) {
                return false;
            }
        }
        return true;
    }

    private static TierDefinition parseTier(final ResourceLocation source, final JsonObject json) {
        final ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "id"));
        final TierDomain domain = TierDomain.valueOf(GsonHelper.getAsString(json, "domain").toUpperCase(java.util.Locale.ROOT));
        final int schemaVersion = GsonHelper.getAsInt(json, "schema_version", 1);
        final Rarity rarity = Rarity.valueOf(GsonHelper.getAsString(json, "display_rarity", "COMMON").toUpperCase(java.util.Locale.ROOT));
        final int sortOrder = GsonHelper.getAsInt(json, "sort_order", 0);
        final int xp = GsonHelper.getAsInt(json, "experience_required", 0);
        final IngredientMatcher ingredient = parseIngredient(GsonHelper.getAsJsonObject(json, "upgrade_ingredient"));
        final Optional<IngredientMatcher> downgrade = json.has("downgrade_ingredient") ? Optional.of(parseIngredient(GsonHelper.getAsJsonObject(json, "downgrade_ingredient"))) : Optional.empty();
        final Optional<ResourceLocation> previousTier = json.has("previous_tier") ? Optional.of(new ResourceLocation(GsonHelper.getAsString(json, "previous_tier"))) : Optional.empty();
        final Map<String, Double> attributeMultipliers = parseDoubleMap(json.getAsJsonObject("attribute_multipliers"));
        final Map<String, Double> curiosSetBonuses = parseDoubleMap(json.getAsJsonObject("curios_set_bonuses"));
        final Map<String, Double> compatAmplifiers = parseDoubleMap(json.getAsJsonObject("compat_amplifiers"));
        final boolean generatedUpgradeTemplate = GsonHelper.getAsBoolean(json, "generated_upgrade_template", true);
        final boolean generatedDowngradeTemplate = GsonHelper.getAsBoolean(json, "generated_downgrade_template", true);
        final Optional<ResourceLocation> upgradeTemplateModel = optionalResource(json, "upgrade_template_item_model");
        final Optional<ResourceLocation> downgradeTemplateModel = optionalResource(json, "downgrade_template_item_model");
        final Optional<ResourceLocation> upgradeTemplateTexture = optionalResource(json, "upgrade_template_texture");
        final Optional<ResourceLocation> downgradeTemplateTexture = optionalResource(json, "downgrade_template_texture");
        return new TierDefinition(id, domain, schemaVersion, rarity, sortOrder, xp, ingredient, downgrade, previousTier, Optional.of(source.getNamespace()), attributeMultipliers, curiosSetBonuses, compatAmplifiers, generatedDowngradeTemplate, generatedUpgradeTemplate, upgradeTemplateModel, downgradeTemplateModel, upgradeTemplateTexture, downgradeTemplateTexture);
    }

    private static Optional<ResourceLocation> optionalResource(final JsonObject json, final String key) {
        return json.has(key) ? Optional.of(new ResourceLocation(GsonHelper.getAsString(json, key))) : Optional.empty();
    }

    private static TierEdge parseEdge(final JsonObject json, final int registrationOrder, final String sourcePack) {
        final ResourceLocation from = new ResourceLocation(GsonHelper.getAsString(json, "from"));
        final ResourceLocation to = new ResourceLocation(GsonHelper.getAsString(json, "to"));
        final Optional<IngredientMatcher> ingredient = json.has("attunement_ingredient") ? Optional.of(parseIngredient(GsonHelper.getAsJsonObject(json, "attunement_ingredient"))) : Optional.empty();
        final Optional<ResourceLocation> downgradeTo = json.has("downgrade_to") ? Optional.of(new ResourceLocation(GsonHelper.getAsString(json, "downgrade_to"))) : Optional.empty();
        final Optional<Integer> xpOverride = json.has("experience_required") ? Optional.of(GsonHelper.getAsInt(json, "experience_required")) : Optional.empty();
        final Optional<IngredientMatcher> ingredientOverride = json.has("upgrade_ingredient_override") ? Optional.of(parseIngredient(GsonHelper.getAsJsonObject(json, "upgrade_ingredient_override"))) : Optional.empty();
        final Optional<String> displayBranchId = Optional.ofNullable(GsonHelper.getAsString(json, "display_branch_id", DisplayBranchResolver.branchId(from, to, registrationOrder)));
        final Map<String, Double> compatAmplifiers = parseDoubleMap(json.getAsJsonObject("compat_amplifiers"));
        return new TierEdge(from, to, registrationOrder, ingredient, downgradeTo, Optional.ofNullable(sourcePack), xpOverride, ingredientOverride, displayBranchId, compatAmplifiers);
    }

    private static Map<String, Double> parseDoubleMap(final JsonObject json) {
        if (json == null) {
            return Map.of();
        }
        final Map<String, Double> values = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : json.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsDouble());
        }
        return values;
    }

    private static IngredientMatcher parseIngredient(final JsonObject json) {
        if (json.has("item")) {
            return new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation(GsonHelper.getAsString(json, "item")));
        }
        return new IngredientMatcher(IngredientMatcher.Kind.TAG, new ResourceLocation(GsonHelper.getAsString(json, "tag")));
    }

    private record LoadedData(List<TierDefinition> tiers, List<TierEdge> edges, ValidationReport report) {
    }
}
