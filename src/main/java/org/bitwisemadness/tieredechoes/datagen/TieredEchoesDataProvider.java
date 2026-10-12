package org.bitwisemadness.tieredechoes.datagen;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.data.loader.TieredEchoesDataReloader;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TieredEchoesDataProvider {

    // ... other methods ...

    private static JsonObject templateJson(final TemplateDefinition template) {
        final JsonObject json = new JsonObject();
        json.addProperty("schema_version", 1);
        json.addProperty("id", template.id().toString());
        template.item().ifPresent(item -> json.addProperty("item", item.toString()));
        return json;
    }

    // ... rest of the class methods
}