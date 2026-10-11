package org.bitwisemadness.tieredechoes.common.tier;

import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierDomainTest {

    @Test
    void resolvesFromSerializedName() {
        assertEquals(Optional.of(TierDomain.WEAPON), TierDomain.byName("weapon"));
        assertEquals(Optional.of(TierDomain.SHIELD), TierDomain.byName("shield"));
        assertEquals(Optional.of(TierDomain.RANGED_WEAPONS), TierDomain.byName("ranged_weapons"));
    }

    @Test
    void storageDomainIsGone() {
        assertEquals(Optional.empty(), TierDomain.byName("storage"),
                "storage never shipped a ladder, so it was removed rather than left as a dead name");
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
        assertEquals(List.of(TierDomain.WEAPON, TierDomain.TOOL, TierDomain.ARMOR, TierDomain.SHIELD,
                        TierDomain.RANGED_WEAPONS, TierDomain.ELYTRA, TierDomain.ACCESSORY),
                TierDomain.builtinDomains());
        assertTrue(TierDomain.builtinDomains().contains(TierDomain.ELYTRA),
                "the elytra ships a durability-only ladder, so it belongs in the builtin list");
    }

    /**
     * Every built-in domain needs an item tag, because the tag is the whole of the membership rule —
     * a domain without one could never claim an item and would ship an unreachable ladder.
     */
    @Test
    void everyBuiltinDomainHasAnItemTag() {
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            assertTrue(TieredEchoesTagKeys.forDomain(domain).isPresent(),
                    domain + " ships a ladder but has no item tag");
        }
    }
}
