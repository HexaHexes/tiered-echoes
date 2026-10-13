package org.bitwisemadness.tieredechoes.common.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three arithmetics a datapack can declare, evaluated directly.
 *
 * <p>Pure arithmetic with no Minecraft types, so these stay as fast as a test can be — the config
 * that wraps them into costs and factors is {@link LevelConfigTest}'s business.</p>
 */
class LevelCurveTest {

    @Test
    void theDefaultCostCurveSteeperThanASquareSpendsWhereItPromises() {
        final LevelCurve curve = LevelCurve.polynomial(25.0D, 3.0D);

        // The shipped contract: polynomial, and above 25·n² at every level past the first. A
        // regression to a squared curve would still look like a curve, and this is what tells it
        // apart — the whole point of the default is that later levels cost markedly more.
        for (int level = 2; level <= 10; level++) {
            assertTrue(curve.value(level) > 25.0D * level * level,
                    "level " + level + " must cost more than 25·n², got " + curve.value(level));
        }
        assertEquals(200.0D, curve.value(2), 1.0E-9D);
        assertEquals(25_000.0D, curve.value(10), 1.0E-9D);
    }

    @Test
    void linearMultipliesAndIgnoresItsExponent() {
        final LevelCurve curve = LevelCurve.linear(0.02D);

        assertEquals(0.0D, curve.value(0), 1.0E-12D);
        assertEquals(0.02D, curve.value(1), 1.0E-12D);
        assertEquals(0.18D, curve.value(9), 1.0E-12D);
    }

    @Test
    void exponentialGrowsByItsBasePerLevel() {
        final LevelCurve curve = LevelCurve.exponential(10.0D, 2.0D);

        assertEquals(10.0D, curve.value(0), 1.0E-12D);
        assertEquals(20.0D, curve.value(1), 1.0E-12D);
        assertEquals(2_560.0D, curve.value(8), 1.0E-9D);
    }

    @Test
    void aCurveCannotBeBuiltWithoutParametersThatMeanSomething() {
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.polynomial(0.0D, 3.0D));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.polynomial(-1.0D, 3.0D));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.polynomial(25.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.exponential(10.0D, -2.0D));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.polynomial(Double.NaN, 3.0D));
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(null, 1.0D, 1.0D));
    }
}
