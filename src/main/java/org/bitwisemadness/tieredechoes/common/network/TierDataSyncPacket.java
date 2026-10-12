package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.network.client.TierSyncClientHandler;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

import java.util.List;

/**
 * Server to client payload carrying the tier ladder and the switches that govern it.
 *
 * <p>Reloads are server-side only — {@code AddReloadListenerEvent} never fires on the client — so
 * without this packet a client keeps showing the built-in ladder no matter what a datapack
 * declared. That matters for the creative preview tabs, which read the tier list directly, and for
 * the name and rarity mixins, which decide whether to render a rung at all.</p>
 *
 * <p>The policy travels for the same reason the ladder does, and the copy is the <em>resolved</em>
 * one — the datapack's declaration with the world's gamerules already applied. A client cannot
 * obtain those rules: Minecraft 1.20.1 ships no gamerule sync packet, and {@code
 * Level#getGameRules()} on a client returns a locally built default. Sending the resolved value
 * keeps one question with one answer on both sides.</p>
 */
public final class TierDataSyncPacket {
    private final List<TierDefinition> tiers;
    private final TierPolicy policy;

    public TierDataSyncPacket(final List<TierDefinition> tiers, final TierPolicy policy) {
        this.tiers = List.copyOf(tiers);
        this.policy = policy == null ? TierPolicy.defaults() : policy;
    }

    public List<TierDefinition> tiers() {
        return this.tiers;
    }

    public TierPolicy policy() {
        return this.policy;
    }

    public static void encode(final TierDataSyncPacket packet, final FriendlyByteBuf buf) {
        TierCodec.writeTiers(buf, packet.tiers);
        TierCodec.writePolicy(buf, packet.policy);
    }

    public static TierDataSyncPacket decode(final FriendlyByteBuf buf) {
        return new TierDataSyncPacket(TierCodec.readTiers(buf), TierCodec.readPolicy(buf));
    }

    public static void handle(final TierDataSyncPacket packet) {
        // The handler touches client-only state, so it must never be loaded on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TierSyncClientHandler.apply(packet));
        TieredEchoes.LOGGER.debug("Received tier sync: {} tiers, policy {}", packet.tiers.size(), packet.policy);
    }
}
