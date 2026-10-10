package org.bitwisemadness.tieredechoes.common.template;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One entry from a {@code tier_templates} datapack file.
 *
 * <p>Declaring a template here is what lets a datapack define tiers without also registering
 * Java items: the {@code item} field names whatever should sit in the smithing template slot.</p>
 *
 * <p>A template is keyed by tier alone rather than by tier and domain — one upgrade template and
 * one downgrade template per tier is all the smithing table needs, and the template items
 * themselves were never domain-specific.</p>
 */
public record TemplateDefinition(
		ResourceLocation id,
		ResourceLocation tier,
		boolean downgrade,
		Optional<ResourceLocation> item,
		Optional<ResourceLocation> itemModelOverride,
		Optional<ResourceLocation> textureOverride
) {
	public TemplateDefinition {
		item = item == null ? Optional.empty() : item;
		itemModelOverride = itemModelOverride == null ? Optional.empty() : itemModelOverride;
		textureOverride = textureOverride == null ? Optional.empty() : textureOverride;
	}
}
