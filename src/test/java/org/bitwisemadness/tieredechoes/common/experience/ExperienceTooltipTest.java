package org.bitwisemadness.tieredechoes.common.experience;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceTooltipTest {

    @Test
    void theLineQuotesStoredExperienceAgainstTheRequirement() {
        assertEquals("Experience: 45 / 150", ExperienceTooltip.format(45, 150));
    }

    @Test
    void zeroExperienceStillQuotesTheRequirement() {
        // A fresh item is the case a player most needs the number for.
        assertEquals("Experience: 0 / 25", ExperienceTooltip.format(0, 25));
    }

    @Test
    void aForkIsQuotedAtTheCheapestBranch() {
        // The player crosses the cheap one first, so that is what they are waiting for.
        final OptionalInt threshold = ExperienceTooltip.nextThreshold(List.of(150, 25, 75));

        assertEquals(25, threshold.getAsInt());
    }

    @Test
    void nothingReachableProducesNoLineRatherThanAnEmptyOne() {
        assertTrue(ExperienceTooltip.nextThreshold(List.of()).isEmpty());
    }

    /** A negative cost would render as a bar already filled past its target. */
    @Test
    void negativeRequirementsAreIgnoredRatherThanClamped() {
        assertTrue(ExperienceTooltip.nextThreshold(List.of(-5)).isEmpty());
        assertEquals(10, ExperienceTooltip.nextThreshold(List.of(-5, 10)).getAsInt());
    }
}
