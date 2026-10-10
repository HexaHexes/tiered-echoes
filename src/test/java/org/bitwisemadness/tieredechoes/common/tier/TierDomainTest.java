package org.bitwisemadness.tieredechoes.common.tier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierDomainTest {

    @Test
    void resolvesFromSerializedName() {
        assertEquals(Optional.of(TierDomain.WEAPON), TierDomain.byName("weapon"));
        assertEquals(Optional.of(TierDomain.STORAGE), TierDomain.byName("storage"));
    }

    @Test
    void resolvesFromEnumConstantSoCommandsReadNaturally() {
        assertEquals(Optional.of(TierDomain.ARMOR), TierDomain.byName("ARMOR"));
        assertEquals(Optional.of(TierDomain.ARMOR), TierDomain.byName("armor"));
        assertEquals(Optional.of(TierDomain.ACCESSORY), TierDomain.byName("Accessory"));
    }

    @Test
    void rejectsUnknownOrMissingNames() {
        assertEquals(Optional.empty(), TierDomain.byName("swords"));
        assertEquals(Optional.empty(), TierDomain.byName(""));
        assertEquals(Optional.empty(), TierDomain.byName(null));
    }

    @Test
    void serializedNameIsLowercaseAndRoundTrips() {
        for (final TierDomain domain : TierDomain.values()) {
            assertEquals(domain, TierDomain.byName(domain.getSerializedName()).orElseThrow());
            assertEquals(domain.getSerializedName(), domain.getSerializedName().toLowerCase());
        }
    }

    /**
     * The built-in ladders are generated per domain in these lists, so a domain declared here
     * without matching tier and edge data turns into {@code missing_source_tier} errors on boot.
     */
    @Test
    void builtinDomainsAreExactlyTheOnesThatShipLadders() {
        assertEquals(List.of(TierDomain.WEAPON, TierDomain.TOOL, TierDomain.ARMOR, TierDomain.ACCESSORY),
                TierDomain.builtinDomains());
        assertTrue(TierDomain.builtinDomains().contains(TierDomain.WEAPON));
        assertFalse(TierDomain.builtinDomains().contains(TierDomain.ELYTRA),
                "elytra ships an empty base tag and no ladder, so it must stay out of the builtin list");
    }
}
