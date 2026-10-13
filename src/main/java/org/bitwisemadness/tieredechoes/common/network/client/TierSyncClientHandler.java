package org.bitwisemadness.tieredechoes.common.network.client;

import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.level.LevelService;
import org.bitwisemadness.tieredechoes.common.network.TierDataSyncPacket;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

/**
 * Applies a {@link TierDataSyncPacket} to the client-side services.
 *
 * <p>Kept in a {@code client} package and reached through {@code DistExecutor} so a dedicated server
 * never loads this class — the services it writes to are shared, but the call site must not be.</p>
 */
public final class TierSyncClientHandler {

    private TierSyncClientHandler() {
    }

    public static void apply(final TierDataSyncPacket packet) {
        TierService.getInstance().replaceAll(packet.tiers());
        // What arrived is already resolved against the sending world's gamerules, so it is adopted
        // as-is. Resolving again here would apply a client's own (default, unset) rules to another
        // server's intent.
        PolicyService.getInstance().applyResolved(packet.policy());
        // The curves carry no gamerule half — they are the datapack's answer wherever they are read
        // — so there is nothing to resolve, only to adopt.
        LevelService.getInstance().replaceAll(packet.levelConfig());
        TieredEchoes.LOGGER.debug("Applied synced tier ladder: {} tiers, policy {}, levels {}",
                packet.tiers().size(), packet.policy(), packet.levelConfig());
    }
}
