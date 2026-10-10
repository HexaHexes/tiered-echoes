package org.bitwisemadness.tieredechoes.common.network.client;

import org.bitwisemadness.tieredechoes.TieredEchoes;
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
        PolicyService.getInstance().replace(packet.policy());
        TieredEchoes.LOGGER.debug("Applied synced tier ladder: {} tiers", packet.tiers().size());
    }
}
