package org.bitwisemadness.tieredechoes.common.datagen.packs.base;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ApothicHighTiersBaseDefaultsProvider extends AbstractGeneratedPackProvider {
    public ApothicHighTiersBaseDefaultsProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_base_apothic_high_tiers", "Generated built-in defaults for tieredechoes_defaults_base_apothic_high_tiers");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"apotheosis", "apothichightiers"};
    }
}
