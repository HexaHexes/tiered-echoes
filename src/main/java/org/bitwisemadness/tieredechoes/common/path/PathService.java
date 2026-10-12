package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.api.common.IPathService;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierKey;
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

/**
 * Graph of tier transitions, keyed by {@link TierKey} so that every domain keeps its own edges.
 *
 * <p>Tier ids are shared across domains, so an unscoped {@code (domain, tierId)} key is what stops an
 * armour piece from following a weapon branch and what makes attunement ingredients unambiguous.</p>
 */
public final class PathService implements IPathService {
    private static final PathService INSTANCE = new PathService();
    private final Map<TierKey, List<TierEdge>> outgoingEdges = new LinkedHashMap<>();
    private final Map<TierKey, List<TierEdge>> incomingEdges = new LinkedHashMap<>();

    private PathService() {
        // Empty until the first datapack reload. The transitions that ship inside the jar are the
        // JSON files in the generated datapack, not a second copy of them held in Java - so a
        // datapack that drops a tier really does drop it.
        this.replaceAll(List.of());
    }

    public static PathService getInstance() {
        return INSTANCE;
    }

    public synchronized void replaceAll(final Collection<TierEdge> edges) {
        this.outgoingEdges.clear();
        this.incomingEdges.clear();
        final List<TierEdge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingInt(TierEdge::registrationOrder)
                .thenComparing(edge -> edge.from().toString())
                .thenComparing(edge -> edge.to().toString()));
        for (final TierEdge edge : sorted) {
            final TierKey key = new TierKey(edge.domain(), edge.from());
            this.outgoingEdges.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
            this.incomingEdges.computeIfAbsent(new TierKey(edge.domain(), edge.to()), ignored -> new ArrayList<>()).add(edge);
        }
    }

    public synchronized ValidationReport validate(final TierService tierService) {
        final ValidationReport report = new ValidationReport();
        final Set<TierKey> tierKeys = new LinkedHashSet<>();
        for (final TierDefinition tier : tierService.getAllTiers()) {
            tierKeys.add(TierKey.of(tier));
        }
        for (final TierEdge edge : this.getAllEdgesLike()) {
            if (!tierKeys.contains(new TierKey(edge.domain(), edge.from()))) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "missing_source_tier",
                        "Missing source tier for edge in domain " + edge.domain().getSerializedName(),
                        Map.of("from", edge.from().toString(), "to", edge.to().toString(), "domain", edge.domain().getSerializedName())));
            }
            if (!tierKeys.contains(new TierKey(edge.domain(), edge.to()))) {
                report.add(new ValidationIssue(ValidationIssue.Severity.ERROR, "missing_target_tier",
                        "Missing target tier for edge in domain " + edge.domain().getSerializedName(),
                        Map.of("from", edge.from().toString(), "to", edge.to().toString(), "domain", edge.domain().getSerializedName())));
            }
        }
        reportAttunementAmbiguity(report);
        reportDuplicateBranchIds(report);
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
            this.walk(root, domain, reachable);
            for (final TierDefinition tier : tiers) {
                if (!reachable.contains(tier.id())) {
                    report.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "unreachable_tier",
                            "Tier is not reachable from the first built-in/default tier for its domain",
                            Map.of("domain", domain.getSerializedName(), "tier", tier.id().toString())));
                }
            }
        }
    }

    private void walk(final ResourceLocation tierId, final TierDomain domain, final Set<ResourceLocation> visited) {
        if (!visited.add(tierId)) {
            return;
        }
        for (final TierEdge edge : this.outgoingEdges.getOrDefault(new TierKey(domain, tierId), List.of())) {
            this.walk(edge.to(), domain, visited);
        }
    }

    private void reportAttunementAmbiguity(final ValidationReport report) {
        for (final Map.Entry<TierKey, List<TierEdge>> entry : this.outgoingEdges.entrySet()) {
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
                                    "source_tier", entry.getKey().id().toString(),
                                    "domain", entry.getKey().domain().getSerializedName(),
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

    private void reportDuplicateBranchIds(final ValidationReport report) {
        final Map<String, TierEdge> seen = new LinkedHashMap<>();
        for (final TierEdge edge : this.getAllEdgesLike()) {
            final String branchId = this.effectiveBranchId(edge);
            final TierEdge previous = seen.putIfAbsent(branchId, edge);
            if (previous != null) {
                report.add(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        "duplicate_branch_id",
                        "Two edges share a display branch id; branch selection would be ambiguous.",
                        Map.of(
                                "branch_id", branchId,
                                "domain", edge.domain().getSerializedName(),
                                "first", previous.from() + "->" + previous.to(),
                                "second", edge.from() + "->" + edge.to()
                        )
                ));
            }
        }
    }

    @Override
    public synchronized List<TierEdge> getOutgoingEdges(final ResourceLocation tierId, final TierDomain domain) {
        return List.copyOf(this.outgoingEdges.getOrDefault(new TierKey(domain, tierId), List.of()));
    }

    public synchronized List<TierEdge> getIncomingEdges(final ResourceLocation tierId, final TierDomain domain) {
        return List.copyOf(this.incomingEdges.getOrDefault(new TierKey(domain, tierId), List.of()));
    }

    @Override
    public synchronized Optional<TierEdge> getDowngradeEdge(final ResourceLocation tierId, final TierDomain domain) {
        final List<TierEdge> incoming = this.incomingEdges.getOrDefault(new TierKey(domain, tierId), List.of());
        return incoming.stream().filter(edge -> edge.downgradeTo().isPresent()).min(Comparator.comparingInt(TierEdge::registrationOrder));
    }

    public synchronized Optional<TierEdge> resolveAttunementEdge(final ResourceLocation sourceTierId, final TierDomain domain, final ItemStack offhand) {
        for (final TierEdge edge : this.outgoingEdges.getOrDefault(new TierKey(domain, sourceTierId), List.of())) {
            if (edge.attunementIngredient().isPresent() && edge.attunementIngredient().get().matches(offhand)) {
                return Optional.of(edge);
            }
        }
        return Optional.empty();
    }

    /**
     * The edges out of a rung, always in registration order and never filtered by branch.
     *
     * <p>Branch selection is disabled pending a rework, so a {@code selected_branch_id} — whether
     * left on an item by an older build or written by hand — no longer narrows the choice. The
     * parameter is still accepted because every caller has one and removing it would be churn for no
     * behaviour change; it is simply not consulted. A linear ladder yields its single edge, and a
     * datapack that declares a genuine branch falls back to its configured resolution policy rather
     * than to a selection that nothing can make any more.</p>
     */
    public synchronized List<TierEdge> resolveNextEdges(final ResourceLocation sourceTierId, final TierDomain domain, final Optional<String> selectedBranchId) {
        return List.copyOf(this.outgoingEdges.getOrDefault(new TierKey(domain, sourceTierId), List.of()));
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
        final int seed = this.stableRandomSeed(stack, sourceTierId);
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
}
