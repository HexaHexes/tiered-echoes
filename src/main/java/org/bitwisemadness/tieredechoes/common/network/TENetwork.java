package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

/**
 * The mod's network channel.
 *
 * <p>Tier ladders are defined by datapacks, and datapack reloads only happen on the server, so the
 * client has to be told what the current ladder is. Without this channel the client would keep
 * rendering whatever it loaded at startup, which silently diverges the moment a datapack changes.</p>
 */
public final class TENetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TieredEchoes.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextPacketId = 0;

    private TENetwork() {
    }

    /** Called once during mod construction; must happen before the first network handshake. */
    public static void register() {
        CHANNEL.messageBuilder(TierDataSyncPacket.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TierDataSyncPacket::encode)
                .decoder(TierDataSyncPacket::decode)
                .consumerMainThread((packet, contextSupplier) -> {
                    TierDataSyncPacket.handle(packet);
                    contextSupplier.get().setPacketHandled(true);
                })
                .add();
    }

    /**
     * Sends the ladder and policy currently in force to every connected player. Safe to call during
     * startup, when the player list is simply empty.
     */
    public static void syncToAllOnline() {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void syncTo(final ServerPlayer player) {
        // Re-resolved immediately before sending so that a packet always carries the answer in
        // force now, rather than whatever the last reload or rule change happened to leave behind.
        PolicyService.getInstance().applyOverrides(player.getServer());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), currentPayload());
    }

    private static TierDataSyncPacket currentPayload() {
        return new TierDataSyncPacket(
                TierService.getInstance().getAllTiers(),
                PolicyService.getInstance().get());
    }
}
