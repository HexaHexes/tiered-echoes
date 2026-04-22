package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

public final class IronsSpellsAttributesFeatureProvider extends AbstractGeneratedPackProvider {
    public IronsSpellsAttributesFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_ironsspells_attributes", "Generated built-in defaults for tieredechoes_defaults_feature_ironsspells_attributes");
    }

    @Override
    protected String[] requiredMods() {
        return new String[] {"irons_spellbooks"};
    }
}
