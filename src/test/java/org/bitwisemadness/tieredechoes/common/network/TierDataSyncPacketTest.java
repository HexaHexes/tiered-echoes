package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The whole sync payload as the network layer sees it: the codec writes the ladder, then the
 * policy, and the decoder reads them back in that order. Getting either half wrong is invisible
 * until a client renders a ladder that never existed.
 */
class TierDataSyncPacketTest {

    @Test
    void payloadRoundTripsTiersAndPolicyTogether() {
        final TierPolicy policy = new TierPolicy(TierPolicy.Mode.AUTO_ONLY, TierPolicy.MultiPathResolution.FIRST,
                false, true, Boolean.TRUE);
        final List<TierDefinition> tiers = List.of(
                tier("common", 0, 0, Rarity.COMMON),
                tier("uncommon", 1, 25, Rarity.UNCOMMON));
        final TierDataSyncPacket original = new TierDataSyncPacket(tiers, policy);
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        TierDataSyncPacket.encode(original, buf);
        final TierDataSyncPacket decoded = TierDataSyncPacket.decode(buf);

        assertEquals(tiers, decoded.tiers());
        assertEquals(policy, decoded.policy());
    }

    @Test
    void anEmptyLadderStillEncodesRatherThanOptimisingTheCountAway() {
        final TierDataSyncPacket original = new TierDataSyncPacket(List.of(), TierPolicy.defaults());
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        TierDataSyncPacket.encode(original, buf);
        final TierDataSyncPacket decoded = TierDataSyncPacket.decode(buf);

        assertEquals(List.of(), decoded.tiers());
        assertEquals(TierPolicy.defaults(), decoded.policy());
    }

    /** The constructor defensive-copies, so a caller mutating its list cannot alter a sent payload. */
    @Test
    void payloadDoesNotAliasItsInputList() {
        final java.util.ArrayList<TierDefinition> tiers = new java.util.ArrayList<>();
        tiers.add(tier("common", 0, 0, Rarity.COMMON));
        final TierDataSyncPacket packet = new TierDataSyncPacket(tiers, TierPolicy.defaults());

        tiers.clear();

        assertEquals(1, packet.tiers().size());
    }

    private static TierDefinition tier(final String path, final int sortOrder, final int xp, final Rarity rarity) {
        return new TierDefinition(TestTiers.id(path), TierDomain.WEAPON, 1, rarity, sortOrder, xp,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, TestTiers.id("stick")),
                Optional.empty(), Optional.empty(), Optional.of("test"),
                Map.of(), Map.of(), Map.of(), true, true);
    }
}
