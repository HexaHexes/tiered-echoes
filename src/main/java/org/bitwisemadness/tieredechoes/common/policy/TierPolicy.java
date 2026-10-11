package org.bitwisemadness.tieredechoes.common.policy;

import java.util.Optional;

/**
 * Datapack-driven rules for how tier progression behaves, loaded from {@code tier_policies}.
 *
 * <p>The built-in defaults keep both progression routes open. A datapack can close either one:
 * {@code AUTO_ONLY} removes the smithing recipes and lets experience drive everything, while
 * {@code SMITHING_ONLY} keeps the smithing table as the sole route.</p>
 *
 * @param mode                 which progression routes are available
 * @param multiPathResolution  how a branch is chosen when several are eligible and none is attuned
 * @param carryOverExcessXp    <b>Superseded.</b> Whether xp left over after an upgrade is kept
 *                             rather than spent is now the {@code teResetExperienceOnUpgrade}
 *                             gamerule, and the gamerule wins. The field is still parsed so
 *                             existing packs keep loading, but it no longer reaches gameplay.
 * @param disableUpgradeRecipes explicit kill-switch that applies on top of {@code mode}
 * @param consumeAttunementItem {@code null} means "defer to the server config file"
 */
public record TierPolicy(
        Mode mode,
        MultiPathResolution multiPathResolution,
        boolean carryOverExcessXp,
        boolean disableUpgradeRecipes,
        Boolean consumeAttunementItem
) {

    public enum Mode {
        /** Both XP accumulation and smithing recipes are available. */
        HYBRID,
        /** Only XP accumulation; generated upgrade recipes are not published. */
        AUTO_ONLY,
        /** Only the smithing table; XP is stored but never triggers an upgrade. */
        SMITHING_ONLY
    }

    public enum MultiPathResolution {
        /** Deterministically take the first eligible branch. */
        FIRST,
        /** Pick an eligible branch pseudo-randomly per item. */
        RANDOM
    }

    public TierPolicy {
        mode = mode == null ? Mode.HYBRID : mode;
        multiPathResolution = multiPathResolution == null ? MultiPathResolution.RANDOM : multiPathResolution;
    }

    public static TierPolicy defaults() {
        return new TierPolicy(Mode.HYBRID, MultiPathResolution.RANDOM, true, false, null);
    }

    public boolean autoUpgradeEnabled() {
        return this.mode != Mode.SMITHING_ONLY;
    }

    public boolean upgradeRecipesEnabled() {
        return this.mode != Mode.AUTO_ONLY && !this.disableUpgradeRecipes;
    }

    /**
     * Whether shift-right-click attunement should eat the offhand ingredient. A datapack that states
     * a value wins; otherwise the operator's config decides — see
     * {@link PolicyService#consumeAttunementItem()}.
     */
    public Optional<Boolean> declaredConsumeAttunementItem() {
        return Optional.ofNullable(this.consumeAttunementItem);
    }
}
