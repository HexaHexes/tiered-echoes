package org.bitwisemadness.tieredechoes.common.template;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.Optional;

public record TemplateDefinition(
		ResourceLocation id,
		ResourceLocation tier,
		TierDomain domain,
		boolean downgrade,
		Optional<ResourceLocation> itemModelOverride,
		Optional<ResourceLocation> textureOverride
) {
}
