package org.bitwisemadness.tieredechoes;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.bitwisemadness.tieredechoes.common.compat.apotheosis.ApotheosisCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.arsnouveau.ArsNouveauCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.ironsspells.IronsSpellsCompatBridge;
import org.bitwisemadness.tieredechoes.common.compat.legendarysurvivaloverhaul.LegendarySurvivalOverhaulCompatBridge;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.common.network.TENetwork;
import org.bitwisemadness.tieredechoes.common.registry.TECreativeTabs;
import org.bitwisemadness.tieredechoes.common.registry.TEItems;
import org.bitwisemadness.tieredechoes.common.registry.TERecipes;
import org.slf4j.Logger;

@Mod(TieredEchoes.MOD_ID)
public final class TieredEchoes {
    public static final String MOD_ID = "tieredechoes";
    public static final String MOD_NAME = "Tiered Echoes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TieredEchoes() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        TEItems.ITEMS.register(modBus);
        TERecipes.SERIALIZERS.register(modBus);
        TECreativeTabs.TABS.register(modBus);
        // Channel creation has to happen before the first handshake, so it lives in the constructor
        // rather than in a common-setup listener.
        TENetwork.register();
        modBus.addListener(this::onCommonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TieredEchoesConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing {}", MOD_NAME);
        if (TieredEchoesConfig.COMPAT_AUDIT_MODE.get()) {
            LOGGER.info("[CompatAudit] apotheosis={}, ars_nouveau={}, irons_spellbooks={}, legendarysurvivaloverhaul={}",
                    ApotheosisCompatBridge.getInstance().isLoaded(),
                    ArsNouveauCompatBridge.getInstance().isLoaded(),
                    IronsSpellsCompatBridge.getInstance().isLoaded(),
                    LegendarySurvivalOverhaulCompatBridge.getInstance().isLoaded());
        }
    }
}
