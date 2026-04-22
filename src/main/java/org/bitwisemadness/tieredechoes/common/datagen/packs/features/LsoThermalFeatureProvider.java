package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class LsoThermalFeatureProvider extends AbstractGeneratedPackProvider {
    public LsoThermalFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_lso_thermal", "Generated built-in defaults for tieredechoes_defaults_feature_lso_thermal");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"legendarysurvivaloverhaul"};
    }
}
