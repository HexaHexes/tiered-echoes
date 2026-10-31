package org.bitwisemadness.tieredechoes.common.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

/**
 * Tier ids deliberately repeat across domains ({@code tieredechoes:common} exists four times), so
 * every lookup that forgets to key on domain collapses four distinct tiers into whichever was
 * inserted last. These assertions are what keeps that from regressing quietly.
 */
class TierKeyTest {

    @Test
    void equalDomainAndIdProduceEqualKeys() {
        final ResourceLocation id = TestTiers.id("shared");
        assertEquals(new TierKey(TierDomain.WEAPON, id), new TierKey(TierDomain.WEAPON, id));
        assertEquals(new TierKey(TierDomain.WEAPON, id).hashCode(), new TierKey(TierDomain.WEAPON, id).hashCode());
    }

    @Test
    void sameIdInTwoDomainsProducesDistinctKeys() {
        final ResourceLocation id = TestTiers.id("common");
        assertNotEquals(new TierKey(TierDomain.WEAPON, id), new TierKey(TierDomain.ARMOR, id));
    }

    @Test
    void sameIdInDifferentNamespacesProducesDistinctKeys() {
        assertNotEquals(
                new TierKey(TierDomain.WEAPON, TestTiers.id("common")),
                new TierKey(TierDomain.WEAPON, new ResourceLocation("tieredechoes", "common")));
    }

    @Test
    void ofReadsDomainAndIdFromTheTierItself() {
        final TierDefinition weapon = TestTiers.tier("common", TierDomain.WEAPON, 0);
        final TierDefinition armor = TestTiers.tier("common", TierDomain.ARMOR, 0);

        assertEquals(new TierKey(TierDomain.WEAPON, TestTiers.id("common")), TierKey.of(weapon));
        assertNotEquals(TierKey.of(weapon), TierKey.of(armor));
    }
}
