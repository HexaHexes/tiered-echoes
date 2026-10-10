package org.bitwisemadness.tieredechoes.common.policy;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy record is the switchboard between the two progression routes, so its derived
 * predicates are pinned directly rather than only through the services that read them.
 */
class TierPolicyTest {

    @Test
    void defaultsKeepBothRoutesOpen() {
        final TierPolicy defaults = TierPolicy.defaults();

        assertEquals(TierPolicy.Mode.HYBRID, defaults.mode());
        assertEquals(TierPolicy.MultiPathResolution.RANDOM, defaults.multiPathResolution());
        assertTrue(defaults.carryOverExcessXp());
        assertFalse(defaults.disableUpgradeRecipes());
        assertTrue(defaults.autoUpgradeEnabled());
        assertTrue(defaults.upgradeRecipesEnabled());
        assertEquals(Optional.empty(), defaults.declaredConsumeAttunementItem());
    }

    @Test
    void autoOnlyRemovesTheSmithingRecipesButKeepsXpDrivingProgression() {
        final TierPolicy policy = new TierPolicy(TierPolicy.Mode.AUTO_ONLY, TierPolicy.MultiPathResolution.FIRST,
                true, false, null);

        assertTrue(policy.autoUpgradeEnabled());
        assertFalse(policy.upgradeRecipesEnabled());
    }

    @Test
    void smithingOnlyStopsXpFromEverAdvancingATier() {
        final TierPolicy policy = new TierPolicy(TierPolicy.Mode.SMITHING_ONLY, TierPolicy.MultiPathResolution.FIRST,
                true, false, null);

        assertFalse(policy.autoUpgradeEnabled());
        assertTrue(policy.upgradeRecipesEnabled());
    }

    @Test
    void disableUpgradeRecipesAppliesOnTopOfMode() {
        final TierPolicy policy = new TierPolicy(TierPolicy.Mode.HYBRID, TierPolicy.MultiPathResolution.RANDOM,
                true, true, null);

        assertFalse(policy.upgradeRecipesEnabled(), "the kill-switch must win over HYBRID");
        assertTrue(policy.autoUpgradeEnabled(), "the kill-switch only targets recipes, not xp");
    }

    @Test
    void explicitFalseOnAttunementConsumptionIsStillADeclaration() {
        final TierPolicy policy = new TierPolicy(TierPolicy.Mode.HYBRID, TierPolicy.MultiPathResolution.RANDOM,
                true, false, Boolean.FALSE);

        assertEquals(Optional.of(Boolean.FALSE), policy.declaredConsumeAttunementItem(),
                "an explicit false must not be mistaken for 'not configured'");
    }

    @Test
    void nullComponentsFallBackToSensibleDefaultsRatherThanNpe() {
        final TierPolicy policy = new TierPolicy(null, null, true, false, null);

        assertEquals(TierPolicy.Mode.HYBRID, policy.mode());
        assertEquals(TierPolicy.MultiPathResolution.RANDOM, policy.multiPathResolution());
    }
}
