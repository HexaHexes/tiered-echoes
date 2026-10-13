package org.bitwisemadness.tieredechoes.common.path;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayBranchResolverTest {

    private static final ResourceLocation FROM = TestTiers.id("common");
    private static final ResourceLocation TO = TestTiers.id("uncommon");

    @Test
    void isStableForTheSameTransition() {
        assertEquals(DisplayBranchResolver.branchId(FROM, TO, 10_000), DisplayBranchResolver.branchId(FROM, TO, 10_000));
    }

    @Test
    void separatesDirectionsSoUpgradeAndDowngradeCannotCollide() {
        assertNotEquals(DisplayBranchResolver.branchId(FROM, TO, 10_000), DisplayBranchResolver.branchId(TO, FROM, 10_000));
    }

    /**
     * Branch ids are what two edges are told apart by — validation reports a clash on them and
     * tooltips quote them — so a registration-order change re-using an id would make one route
     * indistinguishable from another across sessions.
     */
    @Test
    void separatesRegistrationOrders() {
        assertNotEquals(DisplayBranchResolver.branchId(FROM, TO, 10_000), DisplayBranchResolver.branchId(FROM, TO, 10_001));
    }

    @Test
    void producesANamespacedFreeIdentifier() {
        assertTrue(DisplayBranchResolver.branchId(FROM, TO, 1).startsWith("branch_"));
    }

    @Test
    void appliesToTheSameEdgeTwiceIdenticallyEvenWhenIdsRepeat() {
        // Tier ids repeat across domains; the resolver does not see a domain, so identical inputs
        // must stay identical and domains have to disambiguate elsewhere (PathService's TierKey).
        final ResourceLocation weaponCommon = new ResourceLocation("tieredechoes", "common");
        assertEquals(DisplayBranchResolver.branchId(weaponCommon, TO, 7),
                DisplayBranchResolver.branchId(weaponCommon, TO, 7));
    }
}
