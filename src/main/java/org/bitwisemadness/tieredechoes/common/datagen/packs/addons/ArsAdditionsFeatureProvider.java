package org.bitwisemadness.tieredechoes.common.datagen.packs.addons;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ArsAdditionsFeatureProvider extends AbstractGeneratedPackProvider {
    public ArsAdditionsFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_ars_addon_ars_additions", "Generated built-in defaults for tieredechoes_defaults_feature_ars_addon_ars_additions");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"ars_nouveau", "ars_additions"};
    }
}
