package org.bitwisemadness.tieredechoes.common.level;

/**
 * Builds the two lines an item's tooltip shows for its level.
 *
 * <p>Kept free of Minecraft types so the wording can be reasoned about — and tested — directly.
 * The event handler does the reading of state and the styling; this only formats what it was
 * given.</p>
 */
public final class LevelTooltip {

    static final String LEVEL_LABEL = "Level: ";
    static final String PROGRESS_LABEL = "XP: ";

    private LevelTooltip() {
    }

    public static String levelLine(final int level) {
        return LEVEL_LABEL + level;
    }

    /**
     * The progress line, quoted as the pool against the very next step.
     *
     * <p>The next step rather than the total remaining to the cap: a player waiting on level 3 of
     * 10 cares what the next kill moves, not what the whole ladder costs. A negative stored value
     * is ignored rather than clamped, so a malformed datapack cannot produce a line that reads as
     * already satisfied.</p>
     */
    public static String progressLine(final int storedExperience, final int requiredExperience) {
        return PROGRESS_LABEL + Math.max(0, storedExperience) + " / " + Math.max(0, requiredExperience);
    }

    /** Shown at the cap, where there is no next step to quote a number for. */
    public static String maxedLine() {
        return PROGRESS_LABEL + "maxed";
    }
}
