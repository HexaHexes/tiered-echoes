package org.bitwisemadness.tieredechoes.common.datagen.packs.base;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ApotheosisBaseDefaultsProvider extends AbstractGeneratedPackProvider {
    public ApotheosisBaseDefaultsProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_base_apotheosis", "Generated built-in defaults for tieredechoes_defaults_base_apotheosis");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"apotheosis"};
    }
}
