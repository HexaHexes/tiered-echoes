package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ArsAttributesFeatureProvider extends AbstractGeneratedPackProvider {
    public ArsAttributesFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_ars_attributes", "Generated built-in defaults for tieredechoes_defaults_feature_ars_attributes");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"ars_nouveau"};
    }
}
