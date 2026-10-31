package org.bitwisemadness.tieredechoes.common.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

/**
 * The rarity enum can only be extended by Forge's runtime extender, which a plain JVM does not run —
 * so this class is where the parts of {@link TERarities} that do not need the extension get pinned,
 * and the extension itself is left to {@code RarityGameTest}.
 */
class TERaritiesTest {

    @Test
    void findExistingReadsVanillaConstantsAndRejectsNamesTheEnumDoesNotHave() {
        assertEquals(Rarity.COMMON, TERarities.findExisting("COMMON"));
        assertEquals(Rarity.EPIC, TERarities.findExisting("EPIC"));
        assertNull(TERarities.findExisting("MYTHIC"), "an unknown name must read as absent, not throw");
        assertNull(TERarities.findExisting("LEGENDARY"),
                "LEGENDARY can only be appended at runtime, which this JVM never does");
    }

    /**
     * The deprecated {@code Rarity#color} field is hardcoded to {@code BLACK} on every constant Forge
     * appends, so reading colour through the style modifier is not a stylistic choice — it is the
     * only reading that survives contact with a foreign rarity. Vanilla constants cannot expose the
     * difference, which is why this pins the modifier path rather than the field's values.
     */
    @Test
    void colourOfGoesThroughTheStyleModifier() {
        assertEquals(0xFFFFFF, TERarities.colourOf(Rarity.COMMON).orElseThrow(),
                "COMMON is white, and the modifier must report it as such");
        assertEquals(0xFF55FF, TERarities.colourOf(Rarity.EPIC).orElseThrow(),
                "EPIC is light purple");
        assertTrue(TERarities.colourOf(null).isEmpty(), "a missing rarity declares no colour");
    }

    @Test
    void byNameResolvesVanillaNamesAndFallsBackRatherThanThrowing() {
        assertEquals(Rarity.RARE, TERarities.byName("RARE", Rarity.COMMON));
        assertEquals(Rarity.RARE, TERarities.byName("  rare  ", Rarity.COMMON), "names are trimmed and upper-cased");
        assertEquals(Rarity.COMMON, TERarities.byName("mythic", Rarity.COMMON));
        assertEquals(Rarity.COMMON, TERarities.byName(null, Rarity.COMMON));
        assertEquals(Rarity.COMMON, TERarities.byName("", Rarity.COMMON));
    }

    /**
     * The probe is what decides whether we append a constant or reuse somebody else's, so it has to
     * agree with the resolver. If these two ever disagree, register() could add a second constant
     * under a name that already resolves.
     */
    @Test
    void theProbeAndTheResolverAlwaysAgree() {
        for (final Rarity rarity : Rarity.values()) {
            assertEquals(rarity, TERarities.findExisting(rarity.name()),
                    rarity + " is in values() but not reachable by name");
            assertEquals(rarity, TERarities.byName(rarity.name(), Rarity.COMMON));
        }
    }
}
