package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.data.loader.ShippedDatapack;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationIssue;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReport;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathServiceTest {

    private PathService service;

    @BeforeEach
    void setUp() {
        TestBootstrap.init();
        this.service = PathService.getInstance();
    }

    @AfterEach
    void tearDown() {
        ShippedDatapack.restoreShippedState();
    }

    @Test
    void shippedLadderCoversEveryStepOfEveryBuiltInDomain() {
        final List<TierEdge> edges = ShippedDatapack.load().edges();

        assertEquals(TierDomain.builtinDomains().size() * 4, edges.size());
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            assertEquals(4, edges.stream().filter(edge -> edge.domain() == domain).count(), domain.toString());
            assertTrue(edges.stream().noneMatch(edge -> edge.downgradeTo().isPresent()),
                    "no shipped step is reversible while downgrades are disabled");
        }
    }

    /** There is no Java fallback any more, so an unreloaded service really is empty. */
    @Test
    void anUnloadedServiceHoldsNoEdgesRatherThanACopyOfTheJar() {
        this.service.replaceAll(List.of());

        assertTrue(this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.WEAPON).isEmpty());
    }

    @Test
    void outgoingEdgesAreScopedToTheirDomain() {
        this.service.replaceAll(List.of(
                TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1, Optional.empty(), Optional.empty(), Optional.empty()),
                TestTiers.edge("common", "uncommon", TierDomain.ARMOR, 2, Optional.empty(), Optional.empty(), Optional.empty())));

        assertEquals(1, this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.WEAPON).size());
        assertEquals(1, this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.ARMOR).size());
        assertEquals(TierDomain.WEAPON,
                this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.WEAPON).get(0).domain());
        assertTrue(this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.TOOL).isEmpty());
    }

    @Test
    void incomingEdgesDriveDowngradeLookups() {
        this.service.replaceAll(List.of(
                TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1, Optional.empty(),
                        Optional.of(TestTiers.id("common")), Optional.empty())));

        final Optional<TierEdge> downgrade = this.service.getDowngradeEdge(TestTiers.id("uncommon"), TierDomain.WEAPON);

        assertEquals(TestTiers.id("common"), downgrade.orElseThrow().downgradeTo().orElseThrow());
        assertEquals(Optional.empty(), this.service.getDowngradeEdge(TestTiers.id("common"), TierDomain.WEAPON));
    }

    @Test
    void branchIdFallsBackToTheTransitionWhenNoDisplayNameWasGiven() {
        final TierEdge explicit = TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1,
                Optional.empty(), Optional.empty(), Optional.of("my_branch"));
        final TierEdge derived = TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 2,
                Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals("my_branch", this.service.effectiveBranchId(explicit));
        assertEquals(TestTiers.id("common") + "->" + TestTiers.id("uncommon"), this.service.effectiveBranchId(derived));
    }

    /**
     * Every route out of a tier is offered, in the order the file declared them, and the graph
     * holds nothing that would choose between them.
     *
     * <p>This is the replacement for branch selection: the recipe the player picks at the smithing
     * table is the choice, so the graph's job is only to be a complete and stable list of what is
     * on offer. Asserting both halves matters — a graph that quietly filtered routes would leave a
     * fork unreachable, and one that reordered them would make a datapack's intent depend on hash
     * order.</p>
     */
    @Test
    void everyRouteOutOfATierIsOfferedInDeclarationOrder() {
        this.service.replaceAll(List.of(
                TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1, Optional.empty(), Optional.empty(),
                        Optional.of("branch_a")),
                TestTiers.edge("common", "rare", TierDomain.WEAPON, 2, Optional.empty(), Optional.empty(),
                        Optional.of("branch_b"))));

        final List<TierEdge> offered = this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.WEAPON);

        assertEquals(2, offered.size(), "a fork must offer both of its routes");
        assertEquals(1, offered.get(0).registrationOrder());
        assertEquals(2, offered.get(1).registrationOrder());
        assertEquals(List.copyOf(offered), this.service.getOutgoingEdges(TestTiers.id("common"), TierDomain.WEAPON),
                "asking twice must produce the same order, or a fork's routes would shuffle between reads");
    }

    @Test
    void validationCatchesAnEdgePointingAtATierThatDoesNotExist() {
        TierService.getInstance().replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0)));
        this.service.replaceAll(List.of(TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1,
                Optional.empty(), Optional.empty(), Optional.empty())));

        final ValidationReport report = this.service.validate(TierService.getInstance());

        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("missing_target_tier")));
        assertTrue(report.errorCount() > 0);
    }

    @Test
    void validationCatchesTwoEdgesClaimingTheSameBranchId() {
        TierService.getInstance().replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0),
                TestTiers.tier("uncommon", TierDomain.WEAPON, 1),
                TestTiers.tier("rare", TierDomain.WEAPON, 2)));
        this.service.replaceAll(List.of(
                TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1, Optional.empty(), Optional.empty(),
                        Optional.of("same_id")),
                TestTiers.edge("common", "rare", TierDomain.WEAPON, 2, Optional.empty(), Optional.empty(),
                        Optional.of("same_id"))));

        final ValidationReport report = this.service.validate(TierService.getInstance());

        assertTrue(report.issues().stream().anyMatch(issue -> issue.type().equals("duplicate_branch_id")));
    }

    @Test
    void aLadderWithNoGapsValidatesClean() {
        TierService.getInstance().replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0),
                TestTiers.tier("uncommon", TierDomain.WEAPON, 1)));
        this.service.replaceAll(List.of(TestTiers.edge("common", "uncommon", TierDomain.WEAPON, 1,
                Optional.of(TestTiers.item("iron_ingot")), Optional.of(TestTiers.id("common")), Optional.empty())));

        final ValidationReport report = this.service.validate(TierService.getInstance());

        assertEquals(0, report.errorCount(), () -> describe(report));
        assertEquals(0, report.warningCount(), () -> describe(report));
    }

    private static String describe(final ValidationReport report) {
        final StringBuilder builder = new StringBuilder();
        for (final ValidationIssue issue : report.issues()) {
            builder.append('\n').append(issue.severity()).append(' ').append(issue.type()).append(" — ").append(issue.message());
        }
        return builder.toString();
    }

    @Test
    void shippedPathGraphValidatesWithoutFindings() {
        ShippedDatapack.install();

        final ValidationReport report = this.service.validate(TierService.getInstance());

        assertEquals(0, report.errorCount(), () -> describe(report));
        assertEquals(0, report.warningCount(), () -> describe(report));
        assertFalse(report.hasErrors());
        assertEquals(TierDomain.builtinDomains().size() * 4, this.service.getAllEdgesLike().size(),
                "the shipped graph is one domain of four linear steps per built-in domain, with built-in duplicates dropped");
    }
}
