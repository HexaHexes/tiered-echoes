package org.bitwisemadness.tieredechoes.common.datagen.packs.addons;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ArsThreadExpansionFeatureProvider extends AbstractGeneratedPackProvider {
    public ArsThreadExpansionFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_ars_addon_arsthread_expansion", "Generated built-in defaults for tieredechoes_defaults_feature_ars_addon_arsthread_expansion");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"ars_nouveau", "arsthreadexpansion"};
    }
}
