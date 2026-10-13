package org.bitwisemadness.tieredechoes.common.level;

/**
 * How the Level system costs experience and pays attributes, as {@code tier_policies} declared it
 * inside the {@code levels} block — or as the defaults describe it when no pack spoke up.
 *
 * <p>Two curves answer two different questions. The <em>experience</em> curve says what advancing
 * costs: {@code experienceToReach(n)} is the price of the step from {@code n-1} to {@code n}, so
 * the level being reached names the price — level 2 under the shipped curve costs
 * {@code 25·2³ = 200}. The <em>attribute</em> curve says what advancing pays: it is wrapped into
 * {@link #levelFactor}, the {@code levelFactor} of {@code effective = 1 + (tierMultiplier − 1) ×
 * levelFactor}.</p>
 *
 * <p>Both curves are evaluated from level 1, and the attribute curve is always offset by a
 * {@code 1 +} so that an item at level 1 sits at factor {@code 1.0}: the tier's own multiplier is
 * fully present the moment the smithing upgrade lands, and every level after that adds a little on
 * top. That offset is why switching the levelling system off — which makes every item read as level
 * 1 — can never make an item weaker than its tier says it should be.</p>
 *
 * @param maxLevel the highest level an item may reach; at least 1
 * @param experience the cost curve, paid in experience per level gained
 * @param attribute the gain curve, scaled against the tier's own multiplier
 */
public record LevelConfig(int maxLevel, LevelCurve experience, LevelCurve attribute) {

    public LevelConfig {
        // Clamped rather than rejected: a max_level of 0 would describe a level system with no
        // levels in it, and a pack that writes one has asked for the smallest possible ladder.
        maxLevel = Math.max(1, maxLevel);
        experience = experience == null ? LevelConfig.defaults().experience() : experience;
        attribute = attribute == null ? LevelConfig.defaults().attribute() : attribute;
    }

    /**
     * Ten levels, a polynomial cost steeper than any square, and two percent of the tier's bonus
     * per level.
     *
     * <p>The cost curve is deliberately cubic: a squared curve spends most of its budget on the
     * last step and makes every earlier level feel free, while a doubling one spends it all on the
     * last step and makes the earlier ones feel pointless. The attribute gain is linear and small —
     * a level is worth a fraction of what a smithing upgrade is worth, which is the whole split
     * between the two systems.</p>
     */
    public static LevelConfig defaults() {
        return new LevelConfig(10, LevelCurve.polynomial(25.0D, 3.0D), LevelCurve.linear(0.02D));
    }

    /**
     * The experience the step from {@code level - 1} to {@code level} costs.
     *
     * <p>Reaching level 1 is free — it is where every item starts — so anything at or below 1
     * costs nothing. Everything else is clamped into the positive int range: a curve a datapack
     * authored can overflow a double long before an item could ever bank that much, and an
     * unreachable cost is a better answer than a wrapped negative one that levels an item for free.</p>
     */
    public int experienceToReach(final int level) {
        if (level <= 1) {
            return 0;
        }
        final double raw = this.experience.value(level);
        if (!Double.isFinite(raw)) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(raw)));
    }

    /**
     * The {@code levelFactor} half of {@code effective = 1 + (tierMultiplier − 1) × levelFactor}.
     *
     * <p>Level 1 is factor 1.0 by construction — the attribute curve is evaluated at
     * {@code level - 1}, which it answers with zero — and each level adds the curve's own shape on
     * top. A level beyond the cap reads as the cap rather than as unbounded growth, and a curve
     * that manages to produce nonsense falls back to 1.0 so a bad datapack cannot paint a NaN into
     * a player's attack damage.</p>
     */
    public double levelFactor(final int level) {
        final int clamped = Math.max(1, Math.min(level, this.maxLevel));
        final double factor = 1.0D + this.attribute.value(clamped - 1);
        return Double.isFinite(factor) && factor >= 1.0D ? factor : 1.0D;
    }
}
