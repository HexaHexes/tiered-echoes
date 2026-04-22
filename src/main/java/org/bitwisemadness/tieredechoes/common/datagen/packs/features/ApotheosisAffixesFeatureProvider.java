package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ApotheosisAffixesFeatureProvider extends AbstractGeneratedPackProvider {
    public ApotheosisAffixesFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_apotheosis_affixes", "Generated built-in defaults for tieredechoes_defaults_feature_apotheosis_affixes");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"apotheosis"};
    }
}
