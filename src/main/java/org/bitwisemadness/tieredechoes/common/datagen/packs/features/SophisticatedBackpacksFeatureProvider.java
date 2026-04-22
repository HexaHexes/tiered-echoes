package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class SophisticatedBackpacksFeatureProvider extends AbstractGeneratedPackProvider {
    public SophisticatedBackpacksFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_sophisticated_backpacks", "Generated built-in defaults for tieredechoes_defaults_feature_sophisticated_backpacks");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"sophisticatedbackpacks"};
    }
}
