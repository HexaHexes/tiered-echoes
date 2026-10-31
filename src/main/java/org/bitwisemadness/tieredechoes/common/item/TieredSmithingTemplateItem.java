package org.bitwisemadness.tieredechoes.common.item;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;

public class TieredSmithingTemplateItem extends SmithingTemplateItem {
	private static final Component APPLIES_TO = Component.translatable("item.tieredechoes.smithing_template.applies_to");
	private static final Component INGREDIENTS = Component.translatable("item.tieredechoes.smithing_template.ingredients");
	private static final Component BASE_SLOT_DESCRIPTION = Component.translatable("item.tieredechoes.smithing_template.base_slot_description");
	private static final Component ADDITIONS_SLOT_DESCRIPTION = Component.translatable("item.tieredechoes.smithing_template.additions_slot_description");

	private static final List<ResourceLocation> BASE_SLOT_EMPTY_ICONS = List.of(
			new ResourceLocation("item/empty_armor_slot_helmet"),
			new ResourceLocation("item/empty_armor_slot_chestplate"),
			new ResourceLocation("item/empty_armor_slot_leggings"),
			new ResourceLocation("item/empty_armor_slot_boots"),
			new ResourceLocation("item/empty_slot_sword"),
			new ResourceLocation("item/empty_slot_pickaxe"),
			new ResourceLocation("item/empty_slot_axe"),
			new ResourceLocation("item/empty_slot_hoe"),
			new ResourceLocation("item/empty_slot_shovel")
	);
	private static final List<ResourceLocation> ADDITIONAL_SLOT_EMPTY_ICONS = List.of(new ResourceLocation("item/empty_slot_ingot"));

	private final Rarity rarity;

	public TieredSmithingTemplateItem(final String tierName, final boolean downgrade, final Rarity rarity) {
		super(
				APPLIES_TO,
				INGREDIENTS,
				Component.translatable("item.tieredechoes.smithing_template." + directionKey(downgrade), displayName(tierName)),
				BASE_SLOT_DESCRIPTION,
				ADDITIONS_SLOT_DESCRIPTION,
				BASE_SLOT_EMPTY_ICONS,
				ADDITIONAL_SLOT_EMPTY_ICONS
		);
		this.rarity = rarity;
	}

	@Override
	public Rarity getRarity(final ItemStack stack) {
		return this.rarity;
	}

	private static String directionKey(final boolean downgrade) {
		return downgrade ? "downgrade_description" : "upgrade_description";
	}

	private static String displayName(final String tierName) {
		final String[] parts = tierName.split("_");
		final StringBuilder result = new StringBuilder();
		for (final String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) {
				result.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return result.toString();
	}
}
