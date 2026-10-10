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
        // Server-side only: this event is fired from ReloadableServerResources.loadResources. The
        // RecipeManager handed over has already finished its own reload, so generated transitions can
        // be merged into it and still reach clients via the update-recipes packet sent afterwards.
        event.addListener(new TieredEchoesDataReloader(event.getServerResources().getRecipeManager()));
    }
}
