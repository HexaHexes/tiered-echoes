package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class CuriosFeatureProvider extends AbstractGeneratedPackProvider {
    public CuriosFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_curios", "Generated built-in defaults for tieredechoes_defaults_feature_curios");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"curios"};
    }
}
