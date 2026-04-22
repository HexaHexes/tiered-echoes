package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PathService implements org.bitwisemadness.tieredechoes.api.common.IPathService {
    private static final PathService INSTANCE = new PathService();
    private final Map<ResourceLocation, List<TierEdge>> outgoingEdges = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<TierEdge>> incomingEdges = new LinkedHashMap<>();
    private int nextRegistrationOrder = 0;

    private PathService() {
        this.bootstrapDefaults();
    }

    public static PathService getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceAll(final Collection<TierEdge> edges) {
        this.outgoingEdges.clear();
        this.incomingEdges.clear();
        final List<TierEdge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(TierEdge::registrationOrder).thenComparing(edge -> edge.from().toString()).thenComparing(edge -> edge.to().toString()));
        for (final TierEdge edge : sorted) {
            this.outgoingEdges.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
            this.incomingEdges.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge);
        }
    }

    public synchronized ValidationReport validate(final TierService tierService) {
        final ValidationReport report = new ValidationReport();
        final Set<ResourceLocation> tierIds = new LinkedHashSet<>();
        for (final TierDefinition tier : tierService.getAllTiers()) {
            tierIds.add(tier.id());
        }
        for (final TierEdge edge : this.getAllEdgesLike()) {
            if (!tierIds.contains(edge.from())) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "missing_source_tier", "Missing source tier for edge", Map.of("from", edge.from().toString(), "to", edge.to().toString())));
            }
            if (!tierIds.contains(edge.to())) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "missing_target_tier", "Missing target tier for edge", Map.of("from", edge.from().toString(), "to", edge.to().toString())));
            }
        }
        reportAttunementAmbiguity(report);
        reportUnreachableTiers(tierService, report);
        return report;
    }

    private void reportUnreachableTiers(final TierService tierService, final ValidationReport report) {
        for (final TierDomain domain : TierDomain.values()) {
            final List<TierDefinition> tiers = tierService.getOrderedTiers(domain);
            if (tiers.isEmpty()) {
                continue;
            }
            final ResourceLocation root = tiers.get(0).id();
            final Set<ResourceLocation> reachable = new LinkedHashSet<>();
            walk(root, reachable);
            for (final TierDefinition tier : tiers) {
                if (!reachable.contains(tier.id())) {
                    report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "unreachable_tier", "Tier is not reachable from the first built-in/default tier for its domain", Map.of("domain", domain.getSerializedName(), "tier", tier.id().toString())));
                }
            }
        }
    }

    private void walk(final ResourceLocation tierId, final Set<ResourceLocation> visited) {
        if (!visited.add(tierId)) {
            return;
        }
        for (final TierEdge edge : this.outgoingEdges.getOrDefault(tierId, List.of())) {
            walk(edge.to(), visited);
        }
    }

    private void reportAttunementAmbiguity(final ValidationReport report) {
        for (final Map.Entry<ResourceLocation, List<TierEdge>> entry : this.outgoingEdges.entrySet()) {
            final Map<String, TierEdge> firstByIngredient = new LinkedHashMap<>();
            for (final TierEdge edge : entry.getValue()) {
                if (edge.attunementIngredient().isEmpty()) {
                    continue;
                }
                final String key = edge.attunementIngredient().get().displayKey();
                if (firstByIngredient.containsKey(key)) {
                    final TierEdge first = firstByIngredient.get(key);
                    report.add(new ValidationIssue(
                            ValidationIssue.Severity.ERROR,
                            "ambiguous_attunement_ingredient",
                            "Ambiguous attunement ingredient for source tier; first registered branch will win.",
                            Map.of(
                                    "source_tier", entry.getKey().toString(),
                                    "ingredient", key,
                                    "selected_edge", first.from() + "->" + first.to(),
                                    "shadowed_edge", edge.from() + "->" + edge.to()
                            )
                    ));
                } else {
                    firstByIngredient.put(key, edge);
                }
            }
        }
    }

    public synchronized List<TierEdge> getOutgoingEdges(final ResourceLocation tierId) {
        return List.copyOf(this.outgoingEdges.getOrDefault(tierId, List.of()));
    }

    public synchronized List<TierEdge> getIncomingEdges(final ResourceLocation tierId) {
        return List.copyOf(this.incomingEdges.getOrDefault(tierId, List.of()));
    }

    public synchronized Optional<TierEdge> getDowngradeEdge(final ResourceLocation tierId) {
        final List<TierEdge> incoming = this.incomingEdges.getOrDefault(tierId, List.of());
        return incoming.stream().filter(edge -> edge.downgradeTo().isPresent()).min(Comparator.comparingInt(TierEdge::registrationOrder));
    }

    public synchronized Optional<TierEdge> resolveAttunementEdge(final ResourceLocation sourceTierId, final ItemStack offhand) {
        for (final TierEdge edge : this.outgoingEdges.getOrDefault(sourceTierId, List.of())) {
            if (edge.attunementIngredient().isPresent() && edge.attunementIngredient().get().matches(offhand)) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    public synchronized List<TierEdge> resolveNextEdges(final ResourceLocation sourceTierId, final Optional<String> selectedBranchId) {
        final List<TierEdge> edges = this.outgoingEdges.getOrDefault(sourceTierId, List.of());
        if (selectedBranchId.isPresent()) {
            return edges.stream().filter(edge -> effectiveBranchId(edge).equals(selectedBranchId.get())).toList();
        }
        return List.copyOf(edges);
    }

    public synchronized String effectiveBranchId(final TierEdge edge) {
        return edge.displayBranchId().orElseGet(() -> edge.from() + "->" + edge.to());
    }

    public synchronized List<TierEdge> getAllEdgesLike() {
        return this.outgoingEdges.values().stream().flatMap(List::stream).toList();
    }

    public synchronized Optional<TierEdge> chooseRandomEligibleEdge(final ItemStack stack, final ResourceLocation sourceTierId, final List<TierEdge> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        final int seed = stableRandomSeed(stack, sourceTierId);
        final int idx = Math.floorMod(seed, candidates.size());
        return Optional.of(candidates.get(idx));
    }

    private int stableRandomSeed(final ItemStack stack, final ResourceLocation sourceTierId) {
        int seed = sourceTierId.hashCode();
        final CompoundTag tag = stack.getTag();
        if (tag != null) {
            seed = 31 * seed + tag.toString().hashCode();
        }
        seed = 31 * seed + stack.getItem().toString().hashCode();
        return seed;
    }

    private void bootstrapDefaults() {
        final List<TierEdge> defaults = new ArrayList<>();
        for (final TierDomain domain : TierDomain.values()) {
            defaults.add(new TierEdge(new ResourceLocation("tieredechoes", "common"), new ResourceLocation("tieredechoes", "uncommon"), this.nextRegistrationOrder++, Optional.of(ingredientForTier("uncommon", domain)), Optional.of(new ResourceLocation("tieredechoes", "common")), Optional.of("builtin"), Optional.empty(), Optional.empty(), Optional.of(domain.getSerializedName() + "_common_to_uncommon"), Map.of()));
            defaults.add(new TierEdge(new ResourceLocation("tieredechoes", "uncommon"), new ResourceLocation("tieredechoes", "rare"), this.nextRegistrationOrder++, Optional.of(ingredientForTier("rare", domain)), Optional.of(new ResourceLocation("tieredechoes", "uncommon")), Optional.of("builtin"), Optional.empty(), Optional.empty(), Optional.of(domain.getSerializedName() + "_uncommon_to_rare"), Map.of()));
            defaults.add(new TierEdge(new ResourceLocation("tieredechoes", "rare"), new ResourceLocation("tieredechoes", "epic"), this.nextRegistrationOrder++, Optional.of(ingredientForTier("epic", domain)), Optional.of(new ResourceLocation("tieredechoes", "rare")), Optional.of("builtin"), Optional.empty(), Optional.empty(), Optional.of(domain.getSerializedName() + "_rare_to_epic"), Map.of()));
        }
        this.replaceAll(defaults);
    }

    private static IngredientMatcher ingredientForTier(final String tier, final TierDomain domain) {
        return switch (tier) {
            case "uncommon" -> new IngredientMatcher(IngredientMatcher.Kind.ITEM, domain == TierDomain.ACCESSORY ? new ResourceLocation("minecraft", "emerald") : new ResourceLocation("minecraft", "iron_ingot"));
            case "rare" -> new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond"));
            case "epic" -> new IngredientMatcher(IngredientMatcher.Kind.ITEM, domain == TierDomain.ACCESSORY ? new ResourceLocation("minecraft", "nether_star") : new ResourceLocation("minecraft", "netherite_ingot"));
            default -> new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "cobblestone"));
        };
    }
}
