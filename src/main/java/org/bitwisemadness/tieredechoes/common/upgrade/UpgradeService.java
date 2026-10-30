package org.bitwisemadness.tieredechoes.common.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.api.common.IUpgradeService;
import org.bitwisemadness.tieredechoes.common.level.LevelService;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the per-domain ladder state carried by an item.
 *
 * <p>State lives under {@code TieredEchoes.domains.<domain>} rather than at the root, because an
 * item in two tags belongs to two domains and climbs each ladder independently. An axe sits on the
 * weapon ladder and the tool ladder at once, and neither ladder's rung may stand in for the
 * other's.</p>
 *
 * <p>This class does not decide <em>when</em> an item moves. There is no accumulated pool to
 * spend and no branch to select; a transition happens because a smithing recipe matched, and the
 * recipe is the only thing that calls in. What is left here is the state — which rung an item sits
 * on — and the reading of it.</p>
 */
public final class UpgradeService implements IUpgradeService {
    private static final UpgradeService INSTANCE = new UpgradeService();

    private UpgradeService() {
    }

    public static UpgradeService getInstance() {
        return INSTANCE;
    }

    @Override
    public ItemStack applyPreviewTier(final ItemStack stack, final TierDefinition tier) {
        final ItemStack copy = stack.copy();
        this.writeTierState(copy, tier, true);
        return copy;
    }

    /**
     * Moves an item onto a rung. Only the ladder being written is touched; an axe's tool rung
     * survives a weapon upgrade untouched, because the two are independent ladders rather than two
     * views of one.
     */
    @Override
    public ItemStack applyTier(final ItemStack stack, final TierDefinition tier) {
        final ItemStack copy = stack.copy();
        this.writeTierState(copy, tier, false);
        this.refreshArsThreadSlotBonus(copy);
        return copy;
    }

    /**
     * The way down, pending the recipe route in {@link org.bitwisemadness.tieredechoes.common.policy.TierPolicy#downgrades}.
     *
     * <p>Returns the stack unchanged rather than throwing, because a caller that has not learned the
     * feature is live should not take the game with it. Nothing generates a downgrade recipe yet, so
     * no smithing table offers one and this is unreachable in ordinary play; it exists as the seam
     * that route writes into.</p>
     */
    @Override
    public ItemStack downgrade(final ItemStack stack, final TierDomain domain) {
        return stack.copy();
    }

    /**
     * The tier the item presents as — the highest rung it holds on any ladder. This is what the name
     * line shows and what a caller with no domain in hand should read.
     */
    public Optional<TierDefinition> getCurrentTier(final ItemStack stack) {
        return TierService.getInstance().resolveCurrentTier(stack, null);
    }

    /**
     * The tier this item holds on one specific ladder, or empty when that ladder has no rung stored.
     */
    public Optional<TierDefinition> getCurrentTier(final ItemStack stack, final TierDomain domain) {
        return TierService.getInstance().resolveCurrentTier(stack, domain);
    }

    /** Every ladder this item currently holds a rung on, keyed by domain. */
    public Map<TierDomain, TierDefinition> getAllCurrentTiers(final ItemStack stack) {
        return TierService.getInstance().readAllTiersFromNbt(stack);
    }

    /**
     * The ladders this item is on.
     *
     * <p>Read from the compounds the item already carries rather than from its tags. An item's tags
     * say which ladders it may join; the compounds say which ladders it is actually on, and the two
     * can disagree — a datapack may drop an item from a tag after it has climbed, and a test may
     * install tiers without binding tags at all.</p>
     *
     * <p>Only an explicit rung counts. An item that has recorded nothing offers no signal about
     * which ladders it wants, so it is treated as belonging to the ones its tags claim.</p>
     */
    public List<TierDomain> ladderDomains(final ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        final Map<TierDomain, TierDefinition> recorded = TierService.getInstance().readAllTiersFromNbt(stack);
        return recorded.isEmpty()
                ? List.copyOf(TierService.getInstance().inferDomains(stack.getItem()))
                : List.copyOf(recorded.keySet());
    }

    public Component describeStack(final ItemStack stack) {
        final Map<TierDomain, TierDefinition> tiers = this.getAllCurrentTiers(stack);
        // Item#getName is used rather than ItemStack#getHoverName so the diagnostic line does not
        // repeat the [Tier] suffix that the name mixin already appends.
        final String baseName = stack.getItem().getName(stack).getString();
        if (tiers.isEmpty()) {
            return Component.literal(baseName).withStyle(ChatFormatting.GRAY);
        }
        final StringBuilder detail = new StringBuilder(baseName);
        for (final Map.Entry<TierDomain, TierDefinition> entry : tiers.entrySet()) {
            detail.append(" | ").append(entry.getKey().getSerializedName())
                    .append(": tier=").append(entry.getValue().id());
        }
        // The level is appended whether or not it has moved: the diagnostic's job is to show the
        // whole state of the item, and "sitting at level one" is an answer worth printing.
        detail.append(" | level=").append(LevelService.getInstance().getLevel(stack))
                .append(", xp=").append(LevelService.getInstance().getStoredExperience(stack));
        return Component.literal(detail.toString()).withStyle(ChatFormatting.GRAY);
    }

    /**
     * Ars Nouveau reads this key straight off the stack's NBT rather than through an accessor, so it
     * stays at the root where that mod looks for it. It describes the item as a whole, so it follows
     * the highest rung the item holds — the same tier the name shows.
     */
    private void refreshArsThreadSlotBonus(final ItemStack stack) {
        final CompoundTag te = stack.getOrCreateTagElement(TierService.ROOT_TAG);
        final ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        final boolean arsArmor = itemId != null
                && "ars_nouveau".equals(itemId.getNamespace())
                && stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
        if (!arsArmor) {
            te.remove("ars_thread_slot_tier_bonus");
            return;
        }
        final int slotTierBonus = this.getCurrentTier(stack)
                .map(tier -> Math.max(0, Math.min(3, tier.sortOrder())))
                .orElse(0);
        if (slotTierBonus <= 0) {
            te.remove("ars_thread_slot_tier_bonus");
            return;
        }
        te.putInt("ars_thread_slot_tier_bonus", slotTierBonus);
    }

    /**
     * Records tier state in NBT only. The visible name is composed at render time by
     * {@code ItemStackNameMixin} so that it follows material upgrades and stays localized.
     */
    private void writeTierState(final ItemStack stack, final TierDefinition tier, final boolean preview) {
        final CompoundTag te = stack.getOrCreateTagElement(TierService.ROOT_TAG);
        te.putInt("schema_version", tier.schemaVersion());
        final CompoundTag domainTag = this.domainTag(stack, tier.domain());
        domainTag.putString(TierService.TIER_ID_TAG, tier.id().toString());
        domainTag.putString(TierService.DISPLAY_RARITY_TAG, tier.displayRarity().name());
        domainTag.putInt(TierService.SORT_ORDER_TAG, tier.sortOrder());
        domainTag.putBoolean("preview", preview);
    }

    /**
     * The compound holding one ladder's state, creating it if this is the first write to that ladder.
     *
     * <p>The child is attached with an explicit {@code put} rather than trusting
     * {@code CompoundTag#getCompound} to create it: that method returns an empty compound for a
     * missing key, but whether it also attaches that compound to the parent is not something this
     * code should depend on. Writing through a detached tag would silently lose every ladder write,
     * which is exactly the sort of failure that only shows up in a player's inventory.</p>
     */
    private CompoundTag domainTag(final ItemStack stack, final TierDomain domain) {
        final CompoundTag te = stack.getOrCreateTagElement(TierService.ROOT_TAG);
        if (!te.contains(TierService.DOMAINS_TAG)) {
            te.put(TierService.DOMAINS_TAG, new CompoundTag());
        }
        final CompoundTag domains = te.getCompound(TierService.DOMAINS_TAG);
        final String key = domain.getSerializedName();
        if (!domains.contains(key)) {
            domains.put(key, new CompoundTag());
        }
        return domains.getCompound(key);
    }
}
