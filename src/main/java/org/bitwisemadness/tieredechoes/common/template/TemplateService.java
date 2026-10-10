package org.bitwisemadness.tieredechoes.common.template;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.registry.TEItems;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves which item belongs in the smithing template slot for a given tier.
 *
 * <p>Template declarations come from {@code tier_templates} datapack files and take precedence over
 * the built-in items, so a datapack can swap the template for a tier without registering anything.</p>
 */
public final class TemplateService {
	private static final TemplateService INSTANCE = new TemplateService();

	/** (tier, downgrade) -> declaration. */
	private final Map<TemplateKey, TemplateDefinition> declared = new LinkedHashMap<>();

	private TemplateService() {
	}

	public static TemplateService getInstance() {
		return INSTANCE;
	}

	public synchronized void replaceAll(final Collection<TemplateDefinition> definitions) {
		this.declared.clear();
		for (final TemplateDefinition definition : definitions) {
			this.declared.put(new TemplateKey(definition.tier(), definition.downgrade()), definition);
		}
	}

	public synchronized Optional<TemplateDefinition> template(final TierDefinition tier, final boolean downgrade) {
		return Optional.ofNullable(this.declared.get(new TemplateKey(tier.id(), downgrade)));
	}

	/**
	 * Every declaration currently in force, in insertion order. Exposed so tooling — and the tests
	 * that pin the one-template-per-tier contract — can see what a datapack actually contributed.
	 */
	public synchronized List<TemplateDefinition> declaredTemplates() {
		return List.copyOf(this.declared.values());
	}

	/**
	 * The item the smithing table consumes in its template slot for this tier. Datapack declarations
	 * win; otherwise the built-in {@code <tier>_upgrade_template} / {@code <tier>_downgrade_template}
	 * items are used when they happen to be registered.
	 */
	public synchronized Optional<Item> templateItem(final TierDefinition tier, final boolean downgrade) {
		final Optional<ResourceLocation> declaredItem = this.template(tier, downgrade).flatMap(TemplateDefinition::item);
		if (declaredItem.isPresent()) {
			final Item item = ForgeRegistries.ITEMS.getValue(declaredItem.get());
			if (item != null && item != Items.AIR) {
				return Optional.of(item);
			}
		}
		final ResourceLocation id = templateId(tier, downgrade);
		final Item item = ForgeRegistries.ITEMS.getValue(id);
		return item == null || item == Items.AIR ? Optional.empty() : Optional.of(item);
	}

	public ResourceLocation templateId(final TierDefinition tier, final boolean downgrade) {
		return new ResourceLocation(TieredEchoes.MOD_ID, tier.id().getPath().toLowerCase(Locale.ROOT) + (downgrade ? "_downgrade_template" : "_upgrade_template"));
	}

	private record TemplateKey(ResourceLocation tier, boolean downgrade) {
	}
}
