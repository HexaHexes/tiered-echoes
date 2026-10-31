package org.bitwisemadness.tieredechoes.common.template;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

/**
 * Resolves which item belongs in the smithing template slot for a given tier.
 *
 * <p>A tier names the templates it uses; this is the map of names to declarations. A tier that names
 * nothing gets the conventional {@code <tier>_upgrade_template} / {@code <tier>_downgrade_template}
 * item, so a ladder that agrees with the built-in naming needs no declarations at all.</p>
 *
 * <p>Template declarations come from {@code tier_templates} datapack files and take precedence over
 * the built-in items, so a datapack can swap the template for a tier without registering anything.</p>
 */
public final class TemplateService {
	private static final TemplateService INSTANCE = new TemplateService();

	/** Template id -> declaration, so a tier can select whichever template it wants by name. */
	private final Map<ResourceLocation, TemplateDefinition> declared = new LinkedHashMap<>();
	private final Object lock = new Object();

	private TemplateService() {
	}

	public static TemplateService getInstance() {
		return INSTANCE;
	}

	public void replaceAll(final Collection<TemplateDefinition> definitions) {
		synchronized (lock) {
			this.declared.clear();
			for (final TemplateDefinition definition : definitions) {
				this.declared.put(definition.id(), definition);
			}
		}
	}

	/**
	 * The template a tier names for the given direction.
	 *
	 * <p>Falls back to the conventional id rather than to an absent declaration, because that id is
	 * also the name of the built-in item — a tier that names nothing still resolves to the template
	 * the mod ships.</p>
	 */
	public ResourceLocation referenceFor(final TierDefinition tier, final boolean downgrade) {
		synchronized (lock) {
			final Optional<ResourceLocation> named = downgrade ? tier.downgradeTemplateId() : tier.upgradeTemplateId();
			return named.orElseGet(() -> templateId(tier, downgrade));
		}
	}

	public Optional<TemplateDefinition> template(final TierDefinition tier, final boolean downgrade) {
		synchronized (lock) {
			return Optional.ofNullable(this.declared.get(this.referenceFor(tier, downgrade)));
		}
	}

	/**
	 * Every declaration currently in force, in insertion order. Exposed so tooling — and the tests
	 * that pin the one-template-per-tier contract — can see what a datapack actually contributed.
	 */
	public List<TemplateDefinition> declaredTemplates() {
		synchronized (lock) {
			return List.copyOf(this.declared.values());
		}
	}

	/**
	 * The name a reference resolves to <em>before</em> the registry is consulted: the declaration's
	 * own item when there is one, and the reference itself when there is not.
	 *
	 * <p>Kept apart from {@link #templateItem} because this is the part that needs no game: two
	 * branches that resolve to the same name are the same slot to a player, which is what the
	 * ambiguity check in {@code PathService} has to be able to see.</p>
	 */
	public ResourceLocation candidateFor(final ResourceLocation reference) {
		synchronized (lock) {
			final TemplateDefinition declaration = this.declared.get(reference);
			return declaration == null ? reference : declaration.item().orElse(reference);
		}
	}

	/**
	 * The item the smithing table consumes in its template slot for this tier. Datapack declarations
	 * win; otherwise the built-in {@code <tier>_upgrade_template} / {@code <tier>_downgrade_template}
	 * items are used when they happen to be registered.
	 */
	public Optional<Item> templateItem(final TierDefinition tier, final boolean downgrade) {
		synchronized (lock) {
			final Item item = ForgeRegistries.ITEMS.getValue(this.candidateFor(this.referenceFor(tier, downgrade)));
			return item == null || item == Items.AIR ? Optional.empty() : Optional.of(item);
		}
	}

	public ResourceLocation templateId(final TierDefinition tier, final boolean downgrade) {
		return new ResourceLocation(TieredEchoes.MOD_ID, tier.id().getPath().toLowerCase(Locale.ROOT) + (downgrade ? "_downgrade_template" : "_upgrade_template"));
	}
}
