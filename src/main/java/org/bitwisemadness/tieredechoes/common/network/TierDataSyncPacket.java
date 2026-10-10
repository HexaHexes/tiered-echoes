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
 * Server to client payload carrying the tier ladder and progression policy that the current
 * datapacks describe.
 *
 * <p>Reloads are server-side only — {@code AddReloadListenerEvent} never fires on the client — so
 * without this packet a client keeps showing the built-in ladder no matter what a datapack declared.
 * That matters for the creative preview tabs, which read the tier list directly.</p>
 *
 * <p>The transition graph is deliberately not sent: it only drives server-side gameplay, and the
 * client has no reason to hold ingredient data it cannot act on.</p>
 */
public final class TierDataSyncPacket {
    private final List<TierDefinition> tiers;
    private final TierPolicy policy;

    public TierDataSyncPacket(final List<TierDefinition> tiers, final TierPolicy policy) {
        this.tiers = List.copyOf(tiers);
        this.policy = policy;
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
        TieredEchoes.LOGGER.debug("Received tier sync: {} tiers, policy {}", packet.tiers.size(), packet.policy.mode());
    }
}
