package org.bitwisemadness.tieredechoes.common.datagen.packs.base;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class ApotheoticAdditionsBaseDefaultsProvider extends AbstractGeneratedPackProvider {
    public ApotheoticAdditionsBaseDefaultsProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_base_apotheotic_additions", "Generated built-in defaults for tieredechoes_defaults_base_apotheotic_additions");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"apotheosis", "apotheotic_additions"};
    }
}
