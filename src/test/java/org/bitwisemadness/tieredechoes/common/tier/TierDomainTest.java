package org.bitwisemadness.tieredechoes.common.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.bitwisemadness.tieredechoes.common.registry.TieredEchoesTagKeys;
import org.junit.jupiter.api.Test;

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
     *
     * <p>Three rather than seven because the other four scale nothing but durability, or are not
     * designed yet. They keep their constants and their tag mappings — {@code
     * everyDomainStillResolvesAnItemTag} pins that — so a datapack can still declare a ladder for
     * them. The mod just does not ship the JSON.</p>
     */
    @Test
    void builtinDomainsAreExactlyTheOnesThatShipLadders() {
        assertEquals(List.of(TierDomain.WEAPON, TierDomain.TOOL, TierDomain.ARMOR),
                TierDomain.builtinDomains());
        assertFalse(TierDomain.builtinDomains().contains(TierDomain.ELYTRA),
                "the elytra scales durability alone, so it ships no ladder until a pack asks for one");
    }

    /**
     * The reason the enum keeps all seven: a datapack that wants a shield ladder needs a tag to put
     * the items in, and a domain whose tag never resolves is a domain nothing can ever join.
     */
    @Test
    void everyDomainStillResolvesAnItemTag() {
        for (final TierDomain domain : TierDomain.values()) {
            assertTrue(TieredEchoesTagKeys.forDomain(domain).isPresent(),
                    domain + " lost its item tag, so no datapack can declare a ladder for it");
        }
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
