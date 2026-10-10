package org.bitwisemadness.tieredechoes.datagen;

import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;

/**
 * Hooks the built-in datapack into {@code gradlew runData}.
 *
 * <p>{@code runData} is deliberately not wired into {@code build}. Booting the registry to rewrite
 * 36 small files makes every build about a minute slower, and the files are committed anyway — so
 * the thing that actually needs guarding is staleness, and {@code ShippedLadderTest} guards it by
 * failing whenever the committed output stops matching {@link ShippedLadder}.</p>
 */
@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TEDataGen {

    private TEDataGen() {
    }

    @SubscribeEvent
    public static void gatherData(final GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new TieredEchoesDataProvider(event.getGenerator().getPackOutput()));
    }
}
