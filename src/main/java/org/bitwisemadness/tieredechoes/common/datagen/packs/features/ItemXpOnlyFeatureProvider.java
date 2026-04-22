package org.bitwisemadness.tieredechoes.common.datagen.packs.features;

import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.bitwisemadness.tieredechoes.common.datagen.packs.support.AbstractGeneratedPackProvider;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class ItemXpOnlyFeatureProvider extends AbstractGeneratedPackProvider {
    public ItemXpOnlyFeatureProvider(final PackOutput output) {
        super(output, "tieredechoes_defaults_feature_item_xp_only", "Generated built-in defaults for tieredechoes_defaults_feature_item_xp_only");
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cachedOutput) {
        final Path root = this.output.getOutputFolder();
        final JsonObject policy = new JsonObject();
        policy.addProperty("schema_version", 1);
        policy.addProperty("mode", "AUTO_ONLY");
        policy.addProperty("disable_upgrade_recipes", true);
        policy.addProperty("multi_path_resolution", "RANDOM");
        policy.addProperty("carry_over_excess_xp", true);
        policy.addProperty("require_all_available_paths_met", true);
        return CompletableFuture.allOf(super.run(cachedOutput), DataProvider.saveStable(cachedOutput, policy, root.resolve("tieredechoes_defaults_feature_item_xp_only/data/tieredechoes_defaults_feature_item_xp_only/dba_policies/item_xp_only.json")));
    }
}
