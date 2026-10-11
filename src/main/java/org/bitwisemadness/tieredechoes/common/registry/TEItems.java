package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.item.TieredSmithingTemplateItem;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TEItems {
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TieredEchoes.MOD_ID);

	/**
	 * The tiers that own a template item, in ladder order.
	 *
	 * <p>There is no {@code tierless} template because there is no tierless tier to name: an item
	 * with no rung recorded is simply untiered, and the smithing step that puts one on the ladder
	 * spends the <em>common</em> template. Naming a template after the tier being entered is what
	 * keeps the slot reading the same way at every position on the ladder.</p>
	 */
	private static final List<String> BUILTIN_TIERS = List.of("common", "uncommon", "rare", "epic", "legendary");
	private static final Map<String, Rarity> BUILTIN_TIER_RARITIES = Map.of(
			"common", Rarity.COMMON,
			"uncommon", Rarity.UNCOMMON,
			"rare", Rarity.RARE,
			"epic", Rarity.EPIC
	);
	public static final Map<String, RegistryObject<Item>> UPGRADE_TEMPLATES = new LinkedHashMap<>();

	static {
		for (final String tier : BUILTIN_TIERS) {
			UPGRADE_TEMPLATES.put(tier, registerTemplate(tier + "_upgrade_template", tier, false, rarityFor(tier)));
		}
	}

	private TEItems() {
	}

	/**
	 * {@code LEGENDARY} is absent from the source enum and only exists once {@link TERarities} has
	 * extended it, so it cannot appear as a literal in the table above.
	 */
	private static Rarity rarityFor(final String tier) {
		if (TERarities.LEGENDARY.equals(tier)) {
			return TERarities.legendary().orElse(Rarity.EPIC);
		}
		return BUILTIN_TIER_RARITIES.getOrDefault(tier, Rarity.COMMON);
	}

	private static RegistryObject<Item> registerTemplate(final String name, final String tier, final boolean downgrade, final Rarity rarity) {
		return ITEMS.register(name, () -> new TieredSmithingTemplateItem(tier, downgrade, rarity));
	}
}
