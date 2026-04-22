package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ArsThreadsFeatureProvider extends AbstractGeneratedPackProvider {
    public ArsThreadsFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_ars_threads", "Generated built-in defaults for tieredechoes_defaults_feature_ars_threads");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"ars_nouveau"};
    }
}
