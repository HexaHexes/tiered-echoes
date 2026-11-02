package org.bitwisemadness.tieredechoes.common.policy;

/**
 * Datapack-driven switches for tier progression, loaded from {@code tier_policies}.
 *
 * <p>Each field is a switch with two halves, and the halves mean different things:
 *
 * <ul>
 *   <li><b>{@code tier_system} / {@code levelling_system}</b> — existence. Switching one off makes
 *       the mod ignore the NBT that system wrote. It is never erased, so a datapack or operator can
 *       turn a system off, ship something else in its place, and turn it back on with the original
 *       state intact.</li>
 *   <li><b>{@code tier_upgrades} / {@code level_up} / {@code downgrades}</b> — progression. The
 *       system still exists and its state still reads, but nothing may move an item along it.</li>
 * </ul>
 *
 * <p>That distinction is why there are five rather than two: an operator has to be able to keep
 * tiers readable while closing the smithing route, and a world that forbids downgrades is not the
 * same as a world that has never heard of them.</p>
 *
 * <p>{@code levelling_system} and {@code level_up} govern the Level system — a per-item experience
 * ladder whose curves live in the {@code levels} block of these same files. The two systems are
 * independent: a level belongs to the item rather than to a ladder, so switching one system off
 * leaves the other's state exactly where it was.</p>
 *
 * <p>Every switch can be overridden per world by a tri-state gamerule; see
 * {@link org.bitwisemadness.tieredechoes.common.gamerule.TEGameRules}. What gameplay reads is the
 * resolved value, held by {@link PolicyService}.</p>
 *
 * @param tierSystem     whether tiered items carry a tier at all
 * @param tierUpgrades   whether the smithing upgrade route is published
 * @param levellingSystem whether items carry a level at all
 * @param levelUp        whether an item may gain a level
 * @param downgrades     whether the smithing downgrade route is published
 */
public record TierPolicy(
        boolean tierSystem,
        boolean tierUpgrades,
        boolean levellingSystem,
        boolean levelUp,
        boolean downgrades
) {

    /**
     * Every switch on but the way down, which is opt-in.
     *
     * <p>A ladder ships no downgrade route unless its pack declares one, so leaving the switch on
     * would make that declaration mean nothing — and a pack that declares a route plainly wants it
     * published. Defaulting it off instead puts the decision where it belongs: on the operator who
     * would rather the smithing table only ever went up.</p>
     */
    public static TierPolicy defaults() {
        return new TierPolicy(true, true, true, true, false);
    }

    /**
     * Whether upgrade recipes may be published.
     *
     * <p>Gated on the system too, because publishing a recipe for a system that is switched off
     * would let a player take a transition whose result the game then refuses to read.</p>
     */
    public boolean upgradesPublished() {
        return this.tierSystem && this.tierUpgrades;
    }

    /** The same gate for the way down; see {@link #downgrades()}. */
    public boolean downgradesPublished() {
        return this.tierSystem && this.downgrades;
    }
}
