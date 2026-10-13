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
import net.minecraft.world.item.crafting.RecipeManager;

import javax.annotation.Nullable;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.compat.apotheosis.ApotheosisCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.arsnouveau.ArsNouveauCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.ironsspells.IronsSpellsCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.legendarysurvivaloverhaul.LegendarySurvivalOverhaulCompatBridge;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReporter;
import org.bitwisemadness.tieredechoes.common.network.TENetwork;
import org.bitwisemadness.tieredechoes.common.path.DisplayBranchResolver;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierKey;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TieredEchoesDataReloader implements PreparableReloadListener {
    /** Datapack directory holding one JSON file per tier: {@code data/<ns>/tiers/<path>.json}. */
    static final String TIER_DIRECTORY = "tiers";
    /** Datapack directory holding the transition graph: {@code data/<ns>/tier_paths/<path>.json}. */
    static final String TIER_PATH_DIRECTORY = "tier_paths";
    /** Datapack directory holding per-tier smithing template definitions. */
    static final String TEMPLATE_DIRECTORY = "tier_templates";
    /** Datapack directory holding progression rules. */
    static final String POLICY_DIRECTORY = "tier_policies";

    /**
     * Server-side only. {@link net.minecraftforge.event.AddReloadListenerEvent} is fired from
     * {@code ReloadableServerResources.loadResources}, which runs after the vanilla recipe reload, so
     * the manager handed to us is already populated and the recipes we inject survive to the
     * {@code ClientboundUpdateRecipesPacket} the server sends afterwards.
     */
    @Nullable
    private final RecipeManager recipeManager;

    public TieredEchoesDataReloader(@Nullable final RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    @Override
    public CompletableFuture<Void> reload(final PreparationBarrier stage, final ResourceManager resourceManager, final ProfilerFiller preparationsProfiler, final ProfilerFiller reloadProfiler, final Executor backgroundExecutor, final Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> load(resourceManager), backgroundExecutor)
                .thenCompose(stage::wait)
                .thenAcceptAsync(this::apply, gameExecutor);
    }

    private static LoadedData load(final ResourceManager manager) {
        // Seed from nothing rather than from the currently applied state: otherwise a tier or path
        // that a datapack stops shipping would be re-applied forever. What the mod itself ships is
        // the generated datapack in its jar, so it arrives through this walk like any other pack.
        final List<TierDefinition> tiers = new ArrayList<>();
        final List<TierEdge> edges = new ArrayList<>();
        final ValidationReport report = new ValidationReport();
        tiers.addAll(loadTiers(manager, report));
        final List<TierDefinition> resolvedTiers = deduplicateTiers(tiers, report);
        edges.addAll(loadEdges(manager, resolvedTiers, report));
        return new LoadedData(resolvedTiers, deduplicateEdges(edges, report), loadTemplates(manager, report), loadPolicy(manager, report), report);
    }

    private void apply(final LoadedData data) {
        // No emptiness guards here on purpose. An empty list is a real answer - a datapack that
        // removed every tier means the ladder really is empty now - and keeping the previous state
        // around instead would make deletion impossible.
        TierService.getInstance().replaceAll(data.tiers);
        PathService.getInstance().replaceAll(data.edges);
        TemplateService.getInstance().replaceAll(data.templates);
        PolicyService.getInstance().replaceFromDatapack(data.policy);
        final ValidationReport combined = data.report;
        PathService.getInstance().validate(TierService.getInstance()).issues().forEach(combined::add);
        // Generated last so the recipes always reflect the path graph that was just applied.
        TierRecipeGenerator.regenerateAndInject(this.recipeManager, combined);
        ValidationReporter.log(combined);
        // Reloads are server-side, so clients are told what the ladder now is.
        TENetwork.syncToAllOnline();
    }

    /**
     * Loads {@code tier_templates} declarations. These name the item that occupies the smithing
     * template slot for a tier, which is what lets a datapack add tiers without registering items.
     */
    private static Collection<TemplateDefinition> loadTemplates(final ResourceManager manager, final ValidationReport report) {
        final List<TemplateDefinition> templates = new ArrayList<>();
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources(TEMPLATE_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                templates.add(parseTemplate(entry.getKey(), json));
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "template_parse_failure", "Failed to parse tier template", Map.of("resource", entry.getKey().toString(), "error", String.valueOf(exception.getMessage()))));
            }
        }
        return templates;
    }

    static TemplateDefinition parseTemplate(final ResourceLocation source, final JsonObject json) {
        final String kind = GsonHelper.getAsString(json, "kind", "upgrade");
        final boolean downgrade = switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "downgrade" -> true;
            case "upgrade" -> false;
            default -> throw new IllegalArgumentException("Unknown template kind \"" + kind + "\"; expected upgrade or downgrade");
        };
        // No domain field: a tier has one upgrade template and one downgrade template regardless of
        // which item type carries the tier, and the template items were never domain-specific. A
        // "domain" key left over from older data files is simply not read.
        return new TemplateDefinition(
                new ResourceLocation(GsonHelper.getAsString(json, "id", source.toString())),
                new ResourceLocation(GsonHelper.getAsString(json, "tier")),
                downgrade,
                optionalResource(json, "item"),
                optionalResource(json, downgrade ? "downgrade_template_item_model" : "upgrade_template_item_model"),
                optionalResource(json, downgrade ? "downgrade_template_texture" : "upgrade_template_texture"));
    }

    /**
     * Loads {@code tier_policies} files, merging them onto the defaults. Fields a file does not
     * mention keep their default, so a two-line datapack can flip a single switch.
     */
    private static TierPolicy loadPolicy(final ResourceManager manager, final ValidationReport report) {
        TierPolicy policy = TierPolicy.defaults();
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources(POLICY_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                policy = mergePolicy(policy, json, report);
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "policy_parse_failure", "Failed to parse tier policy", Map.of("resource", entry.getKey().toString(), "error", String.valueOf(exception.getMessage()))));
            }
        }
        return policy;
    }

    /**
     * Merges one {@code tier_policies} file onto the running answer.
     *
     * <p>Five booleans is the whole shape now. The fields that used to live here answered
     * questions the mod no longer asks — which routes are open collapses onto the switches, and the
     * rest described how accumulated experience should behave — so rather than carry them forward
     * as inert data they are recognised, mapped where a mapping exists, and reported.</p>
     *
     * <p>A superseded key is warned about whether or not its modern replacement is also present, so
     * a pack mid-migration hears about the key it still has to delete rather than only about the one
     * it has not added yet. Where both appear in one file the modern key governs: adding the new
     * key should take effect before the old one is gone, not after.</p>
     */
    static TierPolicy mergePolicy(final TierPolicy base, final JsonObject json, final ValidationReport report) {
        boolean tierSystem = GsonHelper.getAsBoolean(json, "tier_system", base.tierSystem());
        boolean tierUpgrades = GsonHelper.getAsBoolean(json, "tier_upgrades", base.tierUpgrades());

        // Kept as a named alias rather than dropped outright: it was the one legacy key a pack had a
        // reason to set, and its meaning is the same switch turned around.
        if (json.has("disable_upgrade_recipes")) {
            warnSuperseded(report, "disable_upgrade_recipes", "tier_upgrades");
            if (!json.has("tier_upgrades")) {
                tierUpgrades = !GsonHelper.getAsBoolean(json, "disable_upgrade_recipes");
            }
        }
        if (json.has("mode")) {
            warnSuperseded(report, "mode", "tier_system / tier_upgrades");
            // AUTO_ONLY was the only mode that changed behaviour a switch does not describe: it
            // closed the smithing route entirely and let experience drive progression. Experience
            // no longer drives anything, so the surviving half of that intent is "no upgrades".
            if (!json.has("tier_upgrades") && "AUTO_ONLY".equalsIgnoreCase(GsonHelper.getAsString(json, "mode"))) {
                tierUpgrades = false;
            }
            // HYBRID and SMITHING_ONLY both meant "the smithing route is open", which is the default.
        }
        for (final String ignored : List.of("multi_path_resolution", "carry_over_excess_xp", "consume_attunement_item")) {
            if (json.has(ignored)) {
                warnIgnored(report, ignored);
            }
        }

        return new TierPolicy(
                tierSystem,
                tierUpgrades,
                GsonHelper.getAsBoolean(json, "levelling_system", base.levellingSystem()),
                GsonHelper.getAsBoolean(json, "level_up", base.levelUp()),
                GsonHelper.getAsBoolean(json, "downgrades", base.downgrades()));
    }

    private static void warnSuperseded(final ValidationReport report, final String key, final String replacement) {
        report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "superseded_policy_field",
                "'" + key + "' is no longer read; use '" + replacement + "' instead.",
                Map.of("field", key, "replacement", replacement)));
    }

    private static void warnIgnored(final ValidationReport report, final String key) {
        report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "obsolete_policy_field",
                "'" + key + "' described how accumulated item experience behaved, and is parsed only so the file keeps loading.",
                Map.of("field", key)));
    }

    static List<TierDefinition> deduplicateTiers(final List<TierDefinition> tiers, final ValidationReport report) {
        final LinkedHashMap<TierKey, TierDefinition> map = new LinkedHashMap<>();
        for (final TierDefinition tier : tiers) {
            final TierKey key = TierKey.of(tier);
            final TierDefinition shadowed = map.put(key, tier);
            if (shadowed != null && !shadowed.equals(tier)) {
                report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "duplicate_tier_definition",
                        "Two tier files declare the same id in the same domain; the later one wins.",
                        Map.of("domain", tier.domain().getSerializedName(), "tier", tier.id().toString(),
                                "shadowed_source", shadowed.sourcePack().orElse("unknown"),
                                "winning_source", tier.sourcePack().orElse("unknown"))));
            }
        }
        // Ladder order is what sort_order says, not the order the files happened to be enumerated
        // in. A directory walk is alphabetical, which would put epic before uncommon and make
        // anything reading the list in insertion order see the ladder back to front.
        return map.values().stream()
                .sorted(Comparator.comparingInt((TierDefinition tier) -> tier.domain().ordinal())
                        .thenComparingInt(TierDefinition::sortOrder)
                        .thenComparing(tier -> tier.id().toString()))
                .toList();
    }

    /**
     * Drops edges that describe a transition another file already described, keeping the later one.
     *
     * <p>Branching still works because two edges sharing only a <em>source</em> are distinct keys, so
     * a datapack adding a second branch out of the same tier is not a clash.</p>
     */
    static List<TierEdge> deduplicateEdges(final List<TierEdge> edges, final ValidationReport report) {
        final LinkedHashMap<String, TierEdge> byTransition = new LinkedHashMap<>();
        for (final TierEdge edge : edges) {
            // Keyed on the full transition: two branches out of the same source tier are distinct and
            // must both survive, which is exactly what makes branching work.
            final String key = edge.domain().getSerializedName() + "|" + edge.from() + "|" + edge.to();
            final TierEdge shadowed = byTransition.put(key, edge);
            if (shadowed != null && !shadowed.equals(edge)) {
                report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "duplicate_tier_path",
                        "Two tier path files declare the same transition in the same domain; the later one wins.",
                        Map.of("domain", edge.domain().getSerializedName(),
                                "transition", edge.from() + "->" + edge.to(),
                                "shadowed_source", shadowed.sourcePack().orElse("unknown"),
                                "winning_source", edge.sourcePack().orElse("unknown"))));
            }
        }
        return List.copyOf(byTransition.values());
    }

    private static Collection<TierDefinition> loadTiers(final ResourceManager manager, final ValidationReport report) {
        final List<TierDefinition> tiers = new ArrayList<>();
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources(TIER_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                tiers.add(parseTier(entry.getKey(), json));
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "tier_parse_failure", "Failed to parse tier definition", Map.of("resource", entry.getKey().toString(), "error", String.valueOf(exception.getMessage()))));
            }
        }
        return tiers;
    }

    private static Collection<TierEdge> loadEdges(final ResourceManager manager, final List<TierDefinition> tiers, final ValidationReport report) {
        final List<TierEdge> edges = new ArrayList<>();
        int order = 10_000;
        for (final Map.Entry<ResourceLocation, Resource> entry : manager.listResources(TIER_PATH_DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                final JsonObject json = GsonHelper.parse(reader);
                if (!isCompatEligible(json)) {
                    continue;
                }
                final Optional<TierDomain> fileDomain = optionalDomain(json, "domain");
                final JsonArray jsonEdges = GsonHelper.getAsJsonArray(json, "edges", new JsonArray());
                for (final JsonElement element : jsonEdges) {
                    final JsonObject edge = element.getAsJsonObject();
                    final TierDomain domain = resolveEdgeDomain(edge, fileDomain, tiers, report, entry.getKey(), order);
                    if (domain == null) {
                        continue;
                    }
                    edges.add(parseEdge(edge, domain, order++, entry.getKey().toString(), report));
                }
            } catch (final Exception exception) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "path_parse_failure", "Failed to parse tier path", Map.of("resource", entry.getKey().toString(), "error", String.valueOf(exception.getMessage()))));
            }
        }
        return edges;
    }

    /**
     * Every edge must belong to exactly one domain, because tier ids repeat across domains. The file
     * may declare it at the top level, a single edge may override it, and if neither is present it is
     * inferred from which domain actually declares the {@code from} tier — but only when that is
     * unambiguous.
     */
    static TierDomain resolveEdgeDomain(final JsonObject edge, final Optional<TierDomain> fileDomain,
                                                final List<TierDefinition> tiers, final ValidationReport report,
                                                final ResourceLocation resource, final int order) {
        final Optional<TierDomain> edgeLevel = optionalDomain(edge, "domain");
        final Optional<TierDomain> declared = edgeLevel.isPresent() ? edgeLevel : fileDomain;
        if (declared.isPresent()) {
            return declared.get();
        }
        if (!edge.has("from")) {
            report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "path_missing_from",
                    "Edge has no \"from\" tier and no declared domain.",
                    Map.of("resource", resource.toString(), "edge_index", String.valueOf(order))));
            return null;
        }
        final ResourceLocation from = new ResourceLocation(GsonHelper.getAsString(edge, "from"));
        TierDomain resolved = null;
        for (final TierDefinition tier : tiers) {
            if (!tier.id().equals(from)) {
                continue;
            }
            if (resolved != null) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "path_domain_unresolvable",
                        "Edge has no declared \"domain\" and its source tier exists in more than one domain.",
                        Map.of("resource", resource.toString(), "from", from.toString())));
                return null;
            }
            resolved = tier.domain();
        }
        if (resolved == null) {
            report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "path_domain_unresolvable",
                    "Edge has no declared \"domain\" and its source tier is not defined in any domain.",
                    Map.of("resource", resource.toString(), "from", from.toString())));
        }
        return resolved;
    }

    static Optional<TierDomain> optionalDomain(final JsonObject json, final String key) {
        if (!json.has(key)) {
            return Optional.empty();
        }
        final Optional<TierDomain> domain = TierDomain.byName(GsonHelper.getAsString(json, key));
        if (domain.isEmpty()) {
            throw new IllegalArgumentException("Unknown tier domain \"" + GsonHelper.getAsString(json, key) + "\"");
        }
        return domain;
    }

    static boolean isCompatEligible(final JsonObject json) {
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

    static TierDefinition parseTier(final ResourceLocation source, final JsonObject json) {
        final ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "id"));
        final TierDomain domain = TierDomain.valueOf(GsonHelper.getAsString(json, "domain").toUpperCase(java.util.Locale.ROOT));
        final int schemaVersion = GsonHelper.getAsInt(json, "schema_version", 1);
        // Resolved through TERarities so that an unrecognised name reads as COMMON rather than
        // aborting the whole reload, and so that LEGENDARY resolves whether or not the runtime
        // extension that creates it succeeded.
        final Rarity rarity = TERarities.byName(GsonHelper.getAsString(json, "display_rarity", "COMMON"), Rarity.COMMON);
        final int sortOrder = GsonHelper.getAsInt(json, "sort_order", 0);
        if (json.has("experience_required")) {
            // Warned about rather than read: an item no longer earns anything by being used, so a
            // threshold here would gate a transition nothing can reach.
            TieredEchoes.LOGGER.warn("[TieredEchoes] {} declares 'experience_required', which was removed: items no longer earn experience toward a tier.", source);
        }
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
        return new TierDefinition(id, domain, schemaVersion, rarity, sortOrder, ingredient, downgrade, previousTier, Optional.of(source.getNamespace()), attributeMultipliers, curiosSetBonuses, compatAmplifiers, generatedUpgradeTemplate, generatedDowngradeTemplate, upgradeTemplateModel, downgradeTemplateModel, upgradeTemplateTexture, downgradeTemplateTexture);
    }

    private static Optional<ResourceLocation> optionalResource(final JsonObject json, final String key) {
        return json.has(key) ? Optional.of(new ResourceLocation(GsonHelper.getAsString(json, key))) : Optional.empty();
    }

    static TierEdge parseEdge(final JsonObject json, final TierDomain domain, final int registrationOrder,
                              final String sourcePack, final ValidationReport report) {
        final ResourceLocation from = new ResourceLocation(GsonHelper.getAsString(json, "from"));
        final ResourceLocation to = new ResourceLocation(GsonHelper.getAsString(json, "to"));
        if (json.has("attunement_ingredient")) {
            report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "removed_policy_field",
                    "'attunement_ingredient' was removed with attunement; the smithing addition is now spelled 'addition' and is read from that key alone.",
                    Map.of("field", "attunement_ingredient", "replacement", "addition")));
        }
        final Optional<IngredientMatcher> addition = json.has("addition")
                ? Optional.of(parseIngredient(GsonHelper.getAsJsonObject(json, "addition")))
                : Optional.empty();
        final Optional<ResourceLocation> downgradeTo = json.has("downgrade_to") ? Optional.of(new ResourceLocation(GsonHelper.getAsString(json, "downgrade_to"))) : Optional.empty();
        final Optional<String> displayBranchId = Optional.ofNullable(GsonHelper.getAsString(json, "display_branch_id", DisplayBranchResolver.branchId(from, to, registrationOrder)));
        final Map<String, Double> compatAmplifiers = parseDoubleMap(json.getAsJsonObject("compat_amplifiers"));
        return new TierEdge(from, to, domain, registrationOrder, addition, downgradeTo, Optional.ofNullable(sourcePack), displayBranchId, compatAmplifiers);
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

    private record LoadedData(List<TierDefinition> tiers, List<TierEdge> edges, Collection<TemplateDefinition> templates, TierPolicy policy, ValidationReport report) {
    }
}
