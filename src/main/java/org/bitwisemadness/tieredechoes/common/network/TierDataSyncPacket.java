package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.network.client.DisplayEdge;
import org.bitwisemadness.tieredechoes.common.network.client.TierSyncClientHandler;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

import java.util.List;

/**
 * Server to client payload carrying the tier ladder, progression policy, transition graph and
 * gamerule state that the current world and datapacks describe.
 *
 * <p>Reloads are server-side only — {@code AddReloadListenerEvent} never fires on the client — so
 * without this packet a client keeps showing the built-in ladder no matter what a datapack
 * declared. That matters for the creative preview tabs, which read the tier list directly.</p>
 *
 * <p>The transition graph is sent as a reduced projection rather than in full: only the endpoints,
 * the domain, the branch label and the experience override. The full {@code TierEdge} also carries
 * attunement and upgrade ingredients, which are server-side gameplay data the client has no use
 * for. What the client does need is the experience requirement, so it can quote a number in the
 * item tooltip.</p>
 *
 * <p>The gamerules travel here for the same reason. Minecraft 1.20.1 ships no gamerule sync packet
 * at all — {@code ClientLevelData} is built from a difficulty and two flags — so a client reading
 * {@code Level#getGameRules()} gets a locally constructed default, not the server's values. The
 * tooltip gates on these, so they have to come from the server.</p>
 */
public final class TierDataSyncPacket {
    private final List<TierDefinition> tiers;
    private final TierPolicy policy;
    private final List<DisplayEdge> displayEdges;
    private final boolean itemExperienceEnabled;
    private final boolean autoUpgradeEnabled;
    private final boolean resetExperienceOnUpgrade;

    public TierDataSyncPacket(final List<TierDefinition> tiers, final TierPolicy policy,
                              final List<DisplayEdge> displayEdges, final boolean itemExperienceEnabled,
                              final boolean autoUpgradeEnabled, final boolean resetExperienceOnUpgrade) {
        this.tiers = List.copyOf(tiers);
        this.policy = policy;
        this.displayEdges = List.copyOf(displayEdges);
        this.itemExperienceEnabled = itemExperienceEnabled;
        this.autoUpgradeEnabled = autoUpgradeEnabled;
        this.resetExperienceOnUpgrade = resetExperienceOnUpgrade;
    }

    public List<TierDefinition> tiers() {
        return this.tiers;
    }

    public TierPolicy policy() {
        return this.policy;
    }

    public List<DisplayEdge> displayEdges() {
        return this.displayEdges;
    }

    public boolean itemExperienceEnabled() {
        return this.itemExperienceEnabled;
    }

    public boolean autoUpgradeEnabled() {
        return this.autoUpgradeEnabled;
    }

    public boolean resetExperienceOnUpgrade() {
        return this.resetExperienceOnUpgrade;
    }

    public static void encode(final TierDataSyncPacket packet, final FriendlyByteBuf buf) {
        TierCodec.writeTiers(buf, packet.tiers);
        TierCodec.writePolicy(buf, packet.policy);
        TierCodec.writeDisplayEdges(buf, packet.displayEdges);
        buf.writeBoolean(packet.itemExperienceEnabled);
        buf.writeBoolean(packet.autoUpgradeEnabled);
        buf.writeBoolean(packet.resetExperienceOnUpgrade);
    }

    public static TierDataSyncPacket decode(final FriendlyByteBuf buf) {
        return new TierDataSyncPacket(TierCodec.readTiers(buf), TierCodec.readPolicy(buf),
                TierCodec.readDisplayEdges(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(final TierDataSyncPacket packet) {
        // The handler touches client-only state, so it must never be loaded on a dedicated server.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TierSyncClientHandler.apply(packet));
        TieredEchoes.LOGGER.debug("Received tier sync: {} tiers, {} edges, policy {}, itemXp={}, autoUpgrade={}",
                packet.tiers.size(), packet.displayEdges.size(), packet.policy.mode(),
                packet.itemExperienceEnabled, packet.autoUpgradeEnabled);
    }
}
