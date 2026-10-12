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

public final class TemplateService {
	private static final TemplateService INSTANCE = new TemplateService();

	/** template id -> declaration. */
	private final Map<ResourceLocation, TemplateDefinition> declared = new LinkedHashMap<>();

	private TemplateService() {
	}

	public static TemplateService getInstance() {
		return INSTANCE;
	}

	public synchronized void replaceAll(final Collection<TemplateDefinition> definitions) {
		this.declared.clear();
		for (final TemplateDefinition definition : definitions) {
			this.declared.put(definition.id(), definition);
		}
	}

	public synchronized Optional<TemplateDefinition> template(final ResourceLocation templateId) {
		return Optional.ofNullable(this.declared.get(templateId));
	}

	/** Returns the declaration matching this tier's upgrade or downgrade template, if any. */
	public synchronized Optional<TemplateDefinition> template(final TierDefinition tier, final boolean downgrade) {
		final String suffix = downgrade ? "_downgrade" : "_upgrade";
		final ResourceLocation id = new ResourceLocation(TieredEchoes.MOD_ID, tier.id().getPath().toLowerCase(Locale.ROOT) + suffix);
		return this.template(id);
	}

	/** Every declaration currently in force, in insertion order. */
	public synchronized List<TemplateDefinition> declaredTemplates() {
		return List.copyOf(this.declared.values());
	}

	/** The item the smithing table consumes in its template slot for this tier. */
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

	/** The id of the built-in template for this tier (upgrade or downgrade). */
	public ResourceLocation templateId(final TierDefinition tier, final boolean downgrade) {
		return new ResourceLocation(TieredEchoes.MOD_ID, tier.id().getPath().toLowerCase(Locale.ROOT) + (downgrade ? "_downgrade_template" : "_upgrade_template"));
	}
}