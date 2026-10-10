package org.bitwisemadness.tieredechoes.common.datagen;

import lombok.experimental.UtilityClass;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.datagen.packs.addons.ArsAdditionsFeatureProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.addons.ArsThreadExpansionFeatureProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.base.ApotheosisBaseDefaultsProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.base.ApotheoticAdditionsBaseDefaultsProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.base.ApothicHighTiersBaseDefaultsProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.base.VanillaBaseDefaultsProvider;
import org.bitwisemadness.tieredechoes.common.datagen.packs.features.*;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
@UtilityClass
public class TieredEchoesDatagen {

    @SubscribeEvent
    public static void onGatherData(final GatherDataEvent event) {
        final var generator = event.getGenerator();
        final var output = generator.getPackOutput();
        generator.addProvider(event.includeServer(), new VanillaBaseDefaultsProvider(output));
        generator.addProvider(event.includeServer(), new ApotheosisBaseDefaultsProvider(output));
        generator.addProvider(event.includeServer(), new ApotheoticAdditionsBaseDefaultsProvider(output));
        generator.addProvider(event.includeServer(), new ApothicHighTiersBaseDefaultsProvider(output));
        generator.addProvider(event.includeServer(), new VanillaEnchantmentsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ApotheosisAffixesFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ApotheosisSocketsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ApotheosisCharmsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new LsoThermalFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ArsAttributesFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ArsThreadsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new IronsSpellsAttributesFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ArmorSetsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ItemXpFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ItemXpOnlyFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ArsAdditionsFeatureProvider(output));
        generator.addProvider(event.includeServer(), new ArsThreadExpansionFeatureProvider(output));
    }
}
