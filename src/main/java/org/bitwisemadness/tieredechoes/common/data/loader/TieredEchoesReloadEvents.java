package org.bitwisemadness.tieredechoes.common.data.loader;

import lombok.experimental.UtilityClass;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
@UtilityClass
public class TieredEchoesReloadEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(new TieredEchoesDataReloader());
    }
}
