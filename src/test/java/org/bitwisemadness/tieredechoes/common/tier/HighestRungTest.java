package org.bitwisemadness.tieredechoes.common.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * {@link HighestRung} is one rule serving two mixins: the {@code [Tier]} suffix and the name's
 * rarity colour. It needs no registries, so it is tested here rather than in a GameTest — and it is
 * worth testing precisely because the rarity copy of this rule drifted unnoticed until the colour
 * stopped appearing at all.
 */
class HighestRungTest {

    private static CompoundTag entry(final String tierId, final int sortOrder) {
        final CompoundTag compound = new CompoundTag();
        compound.putString(TierService.TIER_ID_TAG, tierId);
        compound.putInt(TierService.SORT_ORDER_TAG, sortOrder);
        return compound;
    }

    private static CompoundTag domains(final String first, final CompoundTag firstEntry,
                                       final String second, final CompoundTag secondEntry) {
        final CompoundTag domains = new CompoundTag();
        domains.put(first, firstEntry);
        if (second != null) {
            domains.put(second, secondEntry);
        }
        return domains;
    }

    @Test
    void anItemOnNoLadderDisplaysNoRung() {
        assertTrue(HighestRung.of(new CompoundTag()).isEmpty(), "an empty compounds has no rung to show");
    }

    @Test
    void anItemOnOneLadderDisplaysThatRung() {
        final CompoundTag domains = domains("weapon", entry("tieredechoes:uncommon", 1), null, null);

        assertEquals("tieredechoes:uncommon",
                HighestRung.of(domains).orElseThrow().getString(TierService.TIER_ID_TAG));
    }

    /** An axe climbs two ladders, and the higher rung is the one the player is actually working toward. */
    @Test
    void theHigherRungWinsAcrossLadders() {
        final CompoundTag domains = domains(
                "weapon", entry("tieredechoes:common", 0),
                "tool", entry("tieredechoes:epic", 3));

        assertEquals("tieredechoes:epic",
                HighestRung.of(domains).orElseThrow().getString(TierService.TIER_ID_TAG));
    }

    /**
     * Two rungs can share a sort order across ladders. NBT iteration order is not stable, so without
     * a deterministic tiebreak the same item could name a different tier between two renders.
     */
    @Test
    void anEqualRungFallsBackToLadderDeclarationOrder() {
        final CompoundTag toolFirst = domains(
                "tool", entry("tieredechoes:rare", 2),
                "weapon", entry("tieredechoes:rare", 2));
        final CompoundTag weaponFirst = domains(
                "weapon", entry("tieredechoes:rare", 2),
                "tool", entry("tieredechoes:rare", 2));

        assertEquals(HighestRung.of(weaponFirst), HighestRung.of(toolFirst),
                "the winner must not depend on the order the tags were written in");
        assertEquals("tieredechoes:rare",
                HighestRung.of(toolFirst).orElseThrow().getString(TierService.TIER_ID_TAG));
    }

    /** A domain an older build knew nothing about still displays, rather than silently vanishing. */
    @Test
    void anUnrecognisedDomainLosesATieRatherThanBeingSkipped() {
        final CompoundTag domains = domains(
                "fromthefuture", entry("tieredechoes:mythic", 2),
                "weapon", entry("tieredechoes:rare", 2));

        assertEquals("tieredechoes:rare",
                HighestRung.of(domains).orElseThrow().getString(TierService.TIER_ID_TAG));
    }

    /** An entry with no tier id has not been written yet, so it must not win on its sort order alone. */
    @Test
    void aLadderThatNamesNoTierDoesNotCompete() {
        final CompoundTag domains = domains(
                "weapon", entry("", 4),
                "tool", entry("tieredechoes:common", 0));

        assertEquals("tieredechoes:common",
                HighestRung.of(domains).orElseThrow().getString(TierService.TIER_ID_TAG));
    }

    @Test
    void aLadderThatNamesNoTierOnItsOwnDisplaysNothing() {
        assertTrue(HighestRung.of(domains("weapon", entry("", 4), null, null)).isEmpty());
    }

    /**
     * Pins the persisted spellings. These strings are written into item NBT and travel with the
     * item, so renaming a constant here would silently orphan every tiered item already in the
     * world — the rarity would stop appearing again, exactly as it did when the read and write
     * sites disagreed.
     */
    @Test
    void thePersistedTagNamesArePinned() {
        assertEquals("TieredEchoes", TierService.ROOT_TAG);
        assertEquals("domains", TierService.DOMAINS_TAG);
        assertEquals("tier_id", TierService.TIER_ID_TAG);
        assertEquals("display_rarity", TierService.DISPLAY_RARITY_TAG);
        assertEquals("tier_sort_order", TierService.SORT_ORDER_TAG);
    }
}
