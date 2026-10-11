package org.bitwisemadness.tieredechoes.common.experience;

import java.util.List;
import java.util.OptionalInt;

/**
 * Builds the experience line shown in an item's tooltip.
 *
 * <p>Kept free of Minecraft types so it can be reasoned about directly. The event handler does the
 * looking-up; this only picks a threshold and renders it.</p>
 */
public final class ExperienceTooltip {

    static final String LABEL = "Experience: ";

    private ExperienceTooltip() {
    }

    /**
     * The lowest cost among the tiers reachable from here, which is the first threshold the item
     * can actually cross. A fork with a cheap branch and an expensive one is quoted at the cheap
     * one, because that is what the player is waiting for.
     *
     * <p>Negative values are ignored rather than clamped, so a malformed datapack asking for
     * {@code -5} cannot produce a line that reads as already satisfied.</p>
     */
    public static OptionalInt nextThreshold(final List<Integer> requirements) {
        return requirements.stream().filter(cost -> cost >= 0).mapToInt(Integer::intValue).min();
    }

    public static String format(final int storedExperience, final int requiredExperience) {
        return LABEL + storedExperience + " / " + requiredExperience;
    }
}
