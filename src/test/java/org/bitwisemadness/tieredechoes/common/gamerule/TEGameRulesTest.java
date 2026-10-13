package org.bitwisemadness.tieredechoes.common.gamerule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three states of a switch, and the fact that there are exactly three.
 *
 * <p>The rule objects themselves need a running server to read, which a unit test has no way to
 * make. What can be reasoned about without one is the mapping from a rule's integer to an answer,
 * and that is where every mistake a pack author could hit lives.</p>
 */
class TEGameRulesTest {

    @Test
    void followingTheDatapackLeavesTheAnswerAlone() {
        assertTrue(TEGameRules.resolve(TEGameRules.FOLLOW_DATAPACK, true));
        assertFalse(TEGameRules.resolve(TEGameRules.FOLLOW_DATAPACK, false),
                "the middle state must be able to say no as well as yes");
    }

    @Test
    void forcingBeatsTheDatapackInBothDirections() {
        assertFalse(TEGameRules.resolve(TEGameRules.FORCE_OFF, true),
                "forcing off must override a datapack that asked for on");
        assertTrue(TEGameRules.resolve(TEGameRules.FORCE_ON, false),
                "forcing on must override a datapack that asked for off");
    }

    @Test
    void forcingIsIdempotent() {
        assertTrue(TEGameRules.resolve(TEGameRules.FORCE_ON, true));
        assertFalse(TEGameRules.resolve(TEGameRules.FORCE_OFF, false));
    }

    /**
     * {@code /gamerule} accepts any integer — {@code IntegerValue} is created without a range — so a
     * nonsense value has to land somewhere defensible. Treating it as "follow the datapack" is the
     * safe answer: the world keeps behaving the way its pack described rather than inverting.
     */
    @Test
    void anythingThatIsNotForcedFallsBackToTheDatapack() {
        for (final int nonsense : new int[]{Integer.MIN_VALUE, -1, 3, 99, Integer.MAX_VALUE}) {
            assertTrue(TEGameRules.resolve(nonsense, true), nonsense + " should follow the datapack");
            assertFalse(TEGameRules.resolve(nonsense, false), nonsense + " should follow the datapack");
        }
    }

    @Test
    void theThreeStatesAreDistinctValues() {
        assertFalse(TEGameRules.FOLLOW_DATAPACK == TEGameRules.FORCE_OFF);
        assertFalse(TEGameRules.FOLLOW_DATAPACK == TEGameRules.FORCE_ON);
        assertFalse(TEGameRules.FORCE_OFF == TEGameRules.FORCE_ON);
    }
}
