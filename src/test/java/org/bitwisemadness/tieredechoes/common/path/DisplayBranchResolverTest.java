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
     * Branch ids are written onto items as {@code last_branch_id} and compared across sessions, so
     * a registration-order change re-using an id would silently carry a player's attunement onto a
     * different edge.
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
