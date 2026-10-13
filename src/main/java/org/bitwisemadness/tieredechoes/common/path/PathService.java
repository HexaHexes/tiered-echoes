package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.api.common.IPathService;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;
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
 * <p>Tier ids are shared across domains, so an unscoped {@code (domain, tierId)} key is what stops
 * an armour piece from following a weapon branch. Which route an item takes is decided entirely by
 * the smithing recipe the player picks — the graph has no opinion of its own about a fork, and
 * holds nothing that would let it choose one.</p>
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
        reportDuplicateBranchIds(report);
        reportAmbiguousBranchSelection(report, tierService);
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

    private void reportDuplicateBranchIds(final ValidationReport report) {
        final Map<String, TierEdge> seen = new LinkedHashMap<>();
        for (final TierEdge edge : this.getAllEdgesLike()) {
            final String branchId = this.effectiveBranchId(edge);
            final TierEdge previous = seen.putIfAbsent(branchId, edge);
            if (previous != null) {
                report.add(new ValidationIssue(
                        ValidationIssue.Severity.ERROR,
                        "duplicate_branch_id",
                        "Two edges share a display branch id; the two routes would render identically.",
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

    /**
     * Reports two routes the smithing table cannot tell apart, one direction at a time.
     *
     * <p>A branch is chosen by what the player puts beside the item: the template names the branch,
     * the addition prices it. Two routes that agree on both therefore describe one transition
     * twice — the table matches whichever it finds first, and the other becomes unreachable with
     * nothing in the pack saying so. That is a different failure from a repeated branch id, which
     * is about how the two <em>render</em> rather than about whether a player can tell them apart
     * at the table at all.</p>
     *
     * <p>Going up, the routes compared are those leaving a rung. Going down, those arriving at one —
     * because the downgrade template every one of them spends is named by the rung being left
     * behind, which is the same rung for all of them, leaving the addition as the only thing left
     * to tell them apart.</p>
     *
     * <p>Templates are compared as names rather than as items because that is what this can reach:
     * the registry that turns a name into an item exists only inside a running game, and two names
     * that agree before it are the same slot after it.</p>
     */
    private void reportAmbiguousBranchSelection(final ValidationReport report, final TierService tierService) {
        this.reportAmbiguousBranches(report, tierService, false);
        this.reportAmbiguousBranches(report, tierService, true);
    }

    private void reportAmbiguousBranches(final ValidationReport report, final TierService tierService, final boolean downgrade) {
        final Map<TierKey, List<TierEdge>> bySource = new LinkedHashMap<>();
        for (final TierEdge edge : this.getAllEdgesLike()) {
            // An edge with no downgrade_to generates no route down, so it cannot be one of a pair.
            if (downgrade && edge.downgradeTo().isEmpty()) {
                continue;
            }
            bySource.computeIfAbsent(new TierKey(edge.domain(), downgrade ? edge.to() : edge.from()), ignored -> new ArrayList<>()).add(edge);
        }
        for (final Map.Entry<TierKey, List<TierEdge>> entry : bySource.entrySet()) {
            final List<TierEdge> branches = entry.getValue();
            for (int first = 0; first < branches.size(); first++) {
                final Optional<BranchKey> chosen = this.resolveBranch(branches.get(first), tierService, downgrade);
                if (chosen.isEmpty()) {
                    // Dangling endpoint, already reported as missing_target_tier; pairing two of
                    // those would blame the ambiguity on the tier that is not there.
                    continue;
                }
                for (int second = first + 1; second < branches.size(); second++) {
                    if (!chosen.equals(this.resolveBranch(branches.get(second), tierService, downgrade))) {
                        continue;
                    }
                    report.add(new ValidationIssue(
                            ValidationIssue.Severity.ERROR,
                            "ambiguous_branch_selection",
                            "Two routes ask for the same template and the same addition, so the smithing table cannot tell them apart and one of them can never be taken.",
                            Map.of(
                                    "domain", entry.getKey().domain().getSerializedName(),
                                    "from", entry.getKey().id().toString(),
                                    "first", branches.get(first).from() + "->" + branches.get(first).to(),
                                    "second", branches.get(second).from() + "->" + branches.get(second).to()
                            )
                    ));
                }
            }
        }
    }

    /**
     * What the smithing table is offered for one route: the template, and the addition beside it.
     *
     * <p>Both directions name their template from the same rung — {@code edge.to()} is the rung
     * being entered going up and the rung being left going down — so only the ingredient each
     * direction falls back to differs. Empty when no recipe would be generated at all, whether
     * because the endpoint is missing or because there is nothing to put in the addition slot:
     * a route that does not exist cannot be one of a pair that cannot be told apart.</p>
     */
    private Optional<BranchKey> resolveBranch(final TierEdge edge, final TierService tierService, final boolean downgrade) {
        final TemplateService templates = TemplateService.getInstance();
        return tierService.getTier(edge.to(), edge.domain())
                .flatMap(target -> edge.additionIngredient()
                        .or(() -> downgrade ? target.downgradeIngredient() : Optional.of(target.upgradeIngredient()))
                        .map(addition -> new BranchKey(
                                templates.candidateFor(templates.referenceFor(target, downgrade)),
                                addition)));
    }

    /** The pair a player actually chooses by: which template goes in, and what accompanies it. */
    private record BranchKey(ResourceLocation template, IngredientMatcher addition) {
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

    public synchronized String effectiveBranchId(final TierEdge edge) {
        return edge.displayBranchId().orElseGet(() -> edge.from() + "->" + edge.to());
    }

    public synchronized List<TierEdge> getAllEdgesLike() {
        return this.outgoingEdges.values().stream().flatMap(List::stream).toList();
    }
}
