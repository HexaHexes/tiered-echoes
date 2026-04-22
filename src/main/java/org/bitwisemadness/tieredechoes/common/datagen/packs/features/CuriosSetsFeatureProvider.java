package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class CuriosSetsFeatureProvider extends AbstractGeneratedPackProvider {
    public CuriosSetsFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_curios_sets", "Generated built-in defaults for tieredechoes_defaults_feature_curios_sets");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"curios"};
    }
}
