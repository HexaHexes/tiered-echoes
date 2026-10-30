package org.bitwisemadness.tieredechoes.common.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped defaults as a player would meet them, and the arithmetic's behaviour at the edges a
 * datapack can push it to.
 *
 * <p>The level-factor assertions are the contract behind {@code effective = 1 + (tierMultiplier −
 * 1) × levelFactor}: factor 1.0 at level 1 is what makes the tier's own multiplier fully present
 * the moment a smithing upgrade lands, and what makes switching the levelling system off — which
 * reads every item as level 1 — incapable of weakening anything.</p>
 */
class LevelConfigTest {

    @Test
    void theShippedDefaultsAreTenCubicLevelsAtTwoPercentEach() {
        final LevelConfig defaults = LevelConfig.defaults();

        assertEquals(10, defaults.maxLevel());
        assertEquals(200, defaults.experienceToReach(2), "level 2 is 25·2³");
        assertEquals(675, defaults.experienceToReach(3), "level 3 is 25·3³");
        assertEquals(25_000, defaults.experienceToReach(10), "the top step is 25·10³");
    }

    @Test
    void everyLevelIsWorthAFractionOfWhatItsTierIsWorth() {
        final LevelConfig defaults = LevelConfig.defaults();

        assertEquals(1.0D, defaults.levelFactor(1), 1.0E-12D,
                "level 1 must present exactly as the tier describes it");
        assertEquals(1.02D, defaults.levelFactor(2), 1.0E-12D);
        assertEquals(1.18D, defaults.levelFactor(10), 1.0E-12D,
                "ten levels are worth a fraction of a smithing step, not a second one");
    }

    @Test
    void reachingTheFirstLevelIsFreeBecauseItIsWhereEveryItemStarts() {
        final LevelConfig defaults = LevelConfig.defaults();

        assertEquals(0, defaults.experienceToReach(1));
        assertEquals(0, defaults.experienceToReach(0));
        assertEquals(0, defaults.experienceToReach(-5));
    }

    /** A level beyond the cap reads as the cap rather than as unbounded growth. */
    @Test
    void aLevelBeyondTheCapReadsAsTheCap() {
        final LevelConfig defaults = LevelConfig.defaults();

        assertEquals(defaults.levelFactor(10), defaults.levelFactor(999), 1.0E-12D);
        assertEquals(1.0D, defaults.levelFactor(0), 1.0E-12D, "and below one reads as one");
    }

    /** A cap of zero describes a level system with no levels in it; the floor is one. */
    @Test
    void aCapCannotFallBelowOneLevel() {
        assertEquals(1, new LevelConfig(0, LevelConfig.defaults().experience(), LevelConfig.defaults().attribute()).maxLevel());
    }

    /** An absurd curve costs an unreachable step, not a wrapped negative one that levels for free. */
    @Test
    void anOverflowingCostSaturatesRatherThanWrapping() {
        final LevelConfig absurd = new LevelConfig(10, LevelCurve.polynomial(1.0E30D, 40.0D), LevelCurve.linear(0.02D));

        assertEquals(Integer.MAX_VALUE, absurd.experienceToReach(10));

        final LevelConfig infinite = new LevelConfig(10, LevelCurve.exponential(1.0E30D, 1.0E30D), LevelCurve.linear(0.02D));

        assertEquals(Integer.MAX_VALUE, infinite.experienceToReach(2));
    }

    /** A factor that manages to produce nonsense falls back rather than painting NaN into damage. */
    @Test
    void aCurveThatWouldPaintNonsenseFallsBackToOne() {
        // The record guards its own parameters, so nonsense has to arrive as arithmetic rather
        // than as data: a coefficient large enough that squaring the level overflows the double.
        // The clamp must still answer a usable number — 1.0, the tier exactly as declared.
        final LevelConfig steep = new LevelConfig(10, LevelConfig.defaults().experience(), LevelCurve.polynomial(1.0E307D, 2.0D));

        assertEquals(1.0D, steep.levelFactor(10), 1.0E-12D,
                "an overflowing attribute curve must not reach the item as Infinity");
    }
}
