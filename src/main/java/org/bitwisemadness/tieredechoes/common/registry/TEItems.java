package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.item.TieredSmithingTemplateItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TEItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TieredEchoes.MOD_ID);

	private static final List<String> BUILTIN_TIERS = List.of("common", "uncommon", "rare", "epic");
	private static final Map<String, Rarity> BUILTIN_TIER_RARITIES = Map.of(
			"common", Rarity.COMMON,
			"uncommon", Rarity.UNCOMMON,
			"rare", Rarity.RARE,
			"epic", Rarity.EPIC
	);
	public static final Map<String, RegistryObject<Item>> UPGRADE_TEMPLATES = new LinkedHashMap<>();
	public static final Map<String, RegistryObject<Item>> DOWNGRADE_TEMPLATES = new LinkedHashMap<>();

	static {
		for (final String tier : BUILTIN_TIERS) {
			final Rarity rarity = BUILTIN_TIER_RARITIES.getOrDefault(tier, Rarity.COMMON);
			UPGRADE_TEMPLATES.put(tier, registerTemplate(tier + "_upgrade_template", tier, false, rarity));
			DOWNGRADE_TEMPLATES.put(tier, registerTemplate(tier + "_downgrade_template", tier, true, rarity));
		}
	}

	private TEItems() {
	}

	private static RegistryObject<Item> registerTemplate(final String name, final String tier, final boolean downgrade, final Rarity rarity) {
		return ITEMS.register(name, () -> new TieredSmithingTemplateItem(tier, downgrade, rarity));
	}
}
