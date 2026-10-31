package org.bitwisemadness.tieredechoes.common.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyServiceTest {

    private PolicyService service;

    @BeforeEach
    void setUp() {
        this.service = PolicyService.getInstance();
    }

    @AfterEach
    void tearDown() {
        this.service.replaceFromDatapack(TierPolicy.defaults());
    }

    @Test
    void startsFromTheDefaults() {
        this.service.replaceFromDatapack(TierPolicy.defaults());

        assertEquals(TierPolicy.defaults(), this.service.get());
        assertTrue(this.service.get().tierSystem());
        assertTrue(this.service.get().tierUpgrades());
    }

    @Test
    void replacingWithNullFallsBackToDefaultsInsteadOfNpeing() {
        this.service.replaceFromDatapack(TierPolicy.defaults());
        this.service.replaceFromDatapack(null);

        assertEquals(TierPolicy.defaults(), this.service.get());
        assertEquals(TierPolicy.defaults(), this.service.declared());
    }

    /**
     * A client adopts a resolved policy as-is. Resolving it again there would apply the client's
     * own local rules — which nobody has ever set — to a server's answer.
     *
     * <p>The declaration stays at the defaults on purpose: a client has no datapack declaration of
     * its own, so keeping it pristine is what stops a later {@code applyOverrides} from quietly
     * re-deriving a local answer over the one the server sent.</p>
     */
    @Test
    void adoptingAResolvedPolicyDoesNotReResolveIt() {
        final TierPolicy forcedOff = new TierPolicy(false, true, true, true, true);

        this.service.applyResolved(forcedOff);

        assertEquals(forcedOff, this.service.get(), "the adopted value must arrive unchanged");
        assertEquals(TierPolicy.defaults(), this.service.declared(),
                "a client never declares anything, so its declaration must not be overwritten either");
    }

    /**
     * The route is gated on the system as well as on the switch, because publishing a recipe for a
     * system that is switched off would let a player take a transition the game then refuses to read.
     */
    @Test
    void aRouteIsOnlyPublishedWhenItsSystemIsAlsoOn() {
        assertTrue(new TierPolicy(true, true, true, true, true).upgradesPublished());
        assertTrue(new TierPolicy(true, true, true, true, true).downgradesPublished());

        assertFalse(new TierPolicy(true, false, true, true, true).upgradesPublished(),
                "closing tier_upgrades alone must close the route");
        assertFalse(new TierPolicy(false, true, true, true, false).upgradesPublished(),
                "switching the tier system off must close every route that belongs to it");
        assertFalse(new TierPolicy(false, true, true, true, true).downgradesPublished(),
                "the way down belongs to the same system");
    }
}
