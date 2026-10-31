package org.bitwisemadness.tieredechoes.common.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The policy record is the switchboard every read site consults, so its derived predicates are
 * pinned directly rather than only through the services that call them.
 *
 * <p>The distinction the whole design rests on — a <em>system</em> is whether something exists, a
 * <em>progression</em> rule is whether something may be gained — lives in these predicates.</p>
 */
class TierPolicyTest {

    @Test
    void defaultsOpenEveryRouteExceptTheWayDown() {
        final TierPolicy defaults = TierPolicy.defaults();

        assertTrue(defaults.tierSystem());
        assertTrue(defaults.tierUpgrades());
        assertTrue(defaults.levellingSystem());
        assertTrue(defaults.levelUp());
        assertFalse(defaults.downgrades(),
                "the way down is opt-in; a pack that declares a route has to ask for it to be published");
        assertFalse(defaults.downgradesPublished());
    }

    @Test
    void fiveBooleansAreExactlyWhatARecordHolds() {
        assertEquals(TierPolicy.class.getRecordComponents().length, 5,
                "every component should be a switch the datapack can flip");
    }

    @Test
    void closingTierUpgradesClosesOnlyTheUpgradeRoute() {
        final TierPolicy policy = new TierPolicy(true, false, true, true, true);

        assertFalse(policy.upgradesPublished());
        assertTrue(policy.downgradesPublished(),
                "the way down is a separate switch and must stay open");
        assertTrue(policy.tierSystem(), "closing a route must not remove the system it belongs to");
        assertTrue(policy.levellingSystem(), "the level system is independent of the tier routes");
    }

    @Test
    void closingTheTierSystemClosesBothRoutesThatBelongToIt() {
        final TierPolicy policy = new TierPolicy(false, true, true, true, true);

        assertFalse(policy.upgradesPublished());
        assertFalse(policy.downgradesPublished());
        assertTrue(policy.tierUpgrades(), "the route's own switch is still declared as on");
    }

    @Test
    void downgradesAreTheirOwnSwitchAndNotAnAfterthoughtOfUpgrades() {
        final TierPolicy closedUpgrades = new TierPolicy(true, false, true, true, true);
        final TierPolicy closedDowngrades = new TierPolicy(true, true, true, true, false);

        assertFalse(closedUpgrades.upgradesPublished());
        assertTrue(closedUpgrades.downgradesPublished());

        assertTrue(closedDowngrades.upgradesPublished(),
                "the way up must not inherit the way down's switch");
        assertFalse(closedDowngrades.downgradesPublished());
    }

    @Test
    void theLevelSystemCanBeKeptWhileLevelingUpIsClosed() {
        final TierPolicy policy = new TierPolicy(true, true, true, false, true);

        assertTrue(policy.levellingSystem(), "items still carry a level");
        assertFalse(policy.levelUp(), "they simply may not gain another one");
    }

    @Test
    void shuttingTheLevelSystemDownDoesNotAlsoClaimLevelUpIsClosed() {
        final TierPolicy policy = new TierPolicy(true, true, false, true, true);

        assertFalse(policy.levellingSystem());
        assertTrue(policy.levelUp(),
                "the two switches are independent, so one does not answer for the other");
    }
}
