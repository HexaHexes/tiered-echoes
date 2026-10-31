package org.bitwisemadness.tieredechoes.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

/**
 * The whole sync payload as the network layer sees it: the codec writes the ladder, then the
 * policy, and the decoder reads them back in that order. Getting either half wrong is invisible
 * until a client renders a ladder that never existed.
 */
class TierDataSyncPacketTest {

    @Test
    void payloadRoundTripsTiersAndPolicyTogether() {
        // A policy with every switch off, because the all-default case would round-trip just as
        // happily if the fields were dropped from the wire entirely.
        final TierPolicy policy = new TierPolicy(false, false, false, false, false);
        final List<TierDefinition> tiers = List.of(tier("common", 0, Rarity.COMMON), tier("uncommon", 1, Rarity.UNCOMMON));
        final TierDataSyncPacket original = new TierDataSyncPacket(tiers, policy);
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        TierDataSyncPacket.encode(original, buf);
        final TierDataSyncPacket decoded = TierDataSyncPacket.decode(buf);

        assertEquals(tiers, decoded.tiers());
        assertEquals(policy, decoded.policy());
    }

    /** Every switch reaches the client, so a forced-off rule does not stop at the server. */
    @Test
    void everySwitchSurvivesTheRoundTrip() {
        for (int bits = 0; bits < 32; bits++) {
            final TierPolicy policy = new TierPolicy(
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);

            final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TierDataSyncPacket.encode(new TierDataSyncPacket(List.of(), policy), buf);

            assertEquals(policy, TierDataSyncPacket.decode(buf).policy(), "bits " + Integer.toBinaryString(bits));
        }
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
        tiers.add(tier("common", 0, Rarity.COMMON));
        final TierDataSyncPacket packet = new TierDataSyncPacket(tiers, TierPolicy.defaults());

        tiers.clear();

        assertEquals(1, packet.tiers().size());
    }

    private static TierDefinition tier(final String path, final int sortOrder, final Rarity rarity) {
        return new TierDefinition(TestTiers.id(path), TierDomain.WEAPON, 1, rarity, sortOrder,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, TestTiers.id("stick")),
                Optional.empty(), Optional.empty(), Optional.of("test"),
                Map.of(), Map.of(), Map.of(), true, true);
    }
}
