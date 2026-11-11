package org.bitwisemadness.tieredechoes.common.level;

/**
 * One arithmetic curve of the Level system, as {@code tier_policies} declared it inside the
 * {@code levels} block.
 *
 * <p>The same record describes both curves the system evaluates, and the kind list is the union of
 * the two slots: an experience curve may be {@code polynomial} or {@code exponential}, an attribute
 * curve may be {@code linear} or {@code polynomial}. Which kinds a slot accepts is a parsing
 * concern — {@link org.bitwisemadness.tieredechoes.common.level.LevelConfig} wraps the raw value in
 * the formula that slot uses, so a curve here is only ever {@code coefficient} and {@code exponent}
 * shaped arithmetic.</p>
 *
 * @param kind        which arithmetic applies
 * @param coefficient the scale of the curve; always positive
 * @param exponent    the power or base of the curve; always positive, ignored by {@code linear}
 */
public record LevelCurve(LevelCurve.Kind kind, double coefficient, double exponent) {

    public LevelCurve {
        if (kind == null) {
            throw new IllegalArgumentException("A level curve needs a kind");
        }
        if (!(coefficient > 0.0D) || !Double.isFinite(coefficient)) {
            throw new IllegalArgumentException("A level curve needs a finite positive coefficient, got " + coefficient);
        }
        if (!(exponent > 0.0D) || !Double.isFinite(exponent)) {
            throw new IllegalArgumentException("A level curve needs a finite positive exponent, got " + exponent);
        }
    }

    /** {@code coefficient · level^exponent}. Used by both slots under their own wrapping. */
    public static LevelCurve polynomial(final double coefficient, final double exponent) {
        return new LevelCurve(Kind.POLYNOMIAL, coefficient, exponent);
    }

    /** {@code coefficient · exponent^level}. Experience only. */
    public static LevelCurve exponential(final double coefficient, final double exponent) {
        return new LevelCurve(Kind.EXPONENTIAL, coefficient, exponent);
    }

    /** {@code coefficient · level}. Attribute only; the exponent is carried but never read. */
    public static LevelCurve linear(final double coefficient) {
        return new LevelCurve(Kind.LINEAR, coefficient, 1.0D);
    }

    /**
     * The raw value at a level.
     *
     * <p>Deliberately unclamped and free of policy: callers decide what a sane output range is,
     * because "the XP this level costs" and "how far the attribute factor moves" have different
     * floors. Callers also pass a level they have already made meaningful — level zero is a legal
     * input here and is what the attribute formula reaches for its very first step.</p>
     */
    public double value(final int level) {
        return switch (this.kind) {
            case POLYNOMIAL -> this.coefficient * Math.pow(level, this.exponent);
            case EXPONENTIAL -> this.coefficient * Math.pow(this.exponent, level);
            case LINEAR -> this.coefficient * level;
        };
    }

    public enum Kind {
        POLYNOMIAL,
        EXPONENTIAL,
        LINEAR
    }
}
