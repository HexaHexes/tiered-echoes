package org.bitwisemadness.tieredechoes.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;

/**
 * Sends the tier ladder to a player as soon as they finish logging in.
 *
 * <p>{@code PlayerLoggedInEvent} fires after the login sequence has completed, so the channel is
 * already established and the payload arrives before the player can open a creative tab or hold an
 * item whose tooltip would need it.</p>
 */
@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
public final class NetworkForgeEvents {

    private NetworkForgeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TENetwork.syncTo(player);
        }
    }
}
