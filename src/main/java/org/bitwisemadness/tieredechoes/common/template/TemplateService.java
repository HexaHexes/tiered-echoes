package org.bitwisemadness.tieredechoes.common.template;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.registry.TEItems;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TemplateService {
	private static final TemplateService INSTANCE = new TemplateService();

	private TemplateService() {
	}

	public static TemplateService getInstance() {
		return INSTANCE;
	}

	public List<TemplateDefinition> getAllTemplates() {
		final List<TemplateDefinition> templates = new ArrayList<>();
		for (final TierDefinition tier : TierService.getInstance().getAllTiers()) {
			if (tier.generatedUpgradeTemplate()) {
				templates.add(new TemplateDefinition(templateId(tier, false), tier.id(), tier.domain(), false, tier.upgradeTemplateItemModel(), tier.upgradeTemplateTexture()));
			}
			if (tier.generatedDowngradeTemplate()) {
				templates.add(new TemplateDefinition(templateId(tier, true), tier.id(), tier.domain(), true, tier.downgradeTemplateItemModel(), tier.downgradeTemplateTexture()));
			}
		}
		return List.copyOf(templates);
	}

	public ResourceLocation templateId(final TierDefinition tier, final boolean downgrade) {
		return new ResourceLocation(TieredEchoes.MOD_ID, tier.id().getPath().toLowerCase(Locale.ROOT) + (downgrade ? "_downgrade_template" : "_upgrade_template"));
	}

	public Optional<Item> templateItem(final TierDefinition tier, final boolean downgrade) {
		final ResourceLocation id = templateId(tier, downgrade);
		final Item item = ForgeRegistries.ITEMS.getValue(id);
		return item == null || item == Items.AIR ? Optional.empty() : Optional.of(item);
	}
}
