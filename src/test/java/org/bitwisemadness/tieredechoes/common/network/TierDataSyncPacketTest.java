package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.level.LevelConfig;
import org.bitwisemadness.tieredechoes.common.level.LevelCurve;
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
        // A policy with every switch off, because the all-default case would round-trip just as
        // happily if the fields were dropped from the wire entirely.
        final TierPolicy policy = new TierPolicy(false, false, false, false, false);
        final List<TierDefinition> tiers = List.of(tier("common", 0, Rarity.COMMON), tier("uncommon", 1, Rarity.UNCOMMON));
        final TierDataSyncPacket original = new TierDataSyncPacket(tiers, policy, LevelConfig.defaults());
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        TierDataSyncPacket.encode(original, buf);
        final TierDataSyncPacket decoded = TierDataSyncPacket.decode(buf);

        assertEquals(tiers, decoded.tiers());
        assertEquals(policy, decoded.policy());
        assertEquals(LevelConfig.defaults(), decoded.levelConfig());
    }

    /** Every switch reaches the client, so a forced-off rule does not stop at the server. */
    @Test
    void everySwitchSurvivesTheRoundTrip() {
        for (int bits = 0; bits < 32; bits++) {
            final TierPolicy policy = new TierPolicy(
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);

            final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TierDataSyncPacket.encode(new TierDataSyncPacket(List.of(), policy, LevelConfig.defaults()), buf);

            assertEquals(policy, TierDataSyncPacket.decode(buf).policy(), "bits " + Integer.toBinaryString(bits));
        }
    }

    /**
     * The curves are the whole of what the progress line quotes, so they have to arrive intact —
     * a client computing the next level's cost from the wrong exponent would show a threshold the
     * server would never charge.
     */
    @Test
    void theLevelConfigSurvivesTheRoundTrip() {
        final LevelConfig config = new LevelConfig(25,
                LevelCurve.exponential(12.5D, 1.8D), LevelCurve.polynomial(0.05D, 1.5D));

        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TierDataSyncPacket.encode(new TierDataSyncPacket(List.of(), TierPolicy.defaults(), config), buf);

        assertEquals(config, TierDataSyncPacket.decode(buf).levelConfig());
    }

    @Test
    void anEmptyLadderStillEncodesRatherThanOptimisingTheCountAway() {
        final TierDataSyncPacket original = new TierDataSyncPacket(List.of(), TierPolicy.defaults(), LevelConfig.defaults());
        final FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        TierDataSyncPacket.encode(original, buf);
        final TierDataSyncPacket decoded = TierDataSyncPacket.decode(buf);

        assertEquals(List.of(), decoded.tiers());
        assertEquals(TierPolicy.defaults(), decoded.policy());
        assertEquals(LevelConfig.defaults(), decoded.levelConfig());
    }

    /** The constructor defensive-copies, so a caller mutating its list cannot alter a sent payload. */
    @Test
    void payloadDoesNotAliasItsInputList() {
        final java.util.ArrayList<TierDefinition> tiers = new java.util.ArrayList<>();
        tiers.add(tier("common", 0, Rarity.COMMON));
        final TierDataSyncPacket packet = new TierDataSyncPacket(tiers, TierPolicy.defaults(), LevelConfig.defaults());

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
