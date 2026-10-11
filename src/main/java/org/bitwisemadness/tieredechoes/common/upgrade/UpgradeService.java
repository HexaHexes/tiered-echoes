package org.bitwisemadness.tieredechoes.common.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.api.common.IUpgradeService;
import org.bitwisemadness.tieredechoes.common.gamerule.ProgressionRules;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.recipe.TierBootstrapRecipe;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the per-domain ladder state carried by an item.
 *
 * <p>State lives under {@code TieredEchoes.domains.<domain>} rather than at the root, because an
 * item in two tags belongs to two domains and climbs each ladder independently. An axe earns weapon
 * experience from kills and tool experience from mining; those two pools must not advance each
 * other, and neither may the branch selection for one ladder stand in for the other's.</p>
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
     * Applies a tier while preserving that ladder's stored experience, and consumes any pending
     * branch selection — the branch belonged to the transition that was just taken. Only the ladder
     * being written is touched; an axe's tool progress survives a weapon upgrade untouched.
     */
    @Override
    public ItemStack applyTier(final ItemStack stack, final TierDefinition tier) {
        final ItemStack copy = stack.copy();
        final TierDomain domain = tier.domain();
        final int currentXp = this.getStoredExperience(copy, domain);
        this.writeTierState(copy, tier, false);
        this.setStoredExperience(copy, domain, currentXp);
        this.clearSelectedBranch(copy, domain);
        this.refreshArsThreadSlotBonus(copy);
        return copy;
    }

    /**
     * Disabled pending a rework of how the way down should be expressed.
     *
     * <p>Returns the stack unchanged rather than throwing, because a caller that has not learned the
     * feature is gone should not take the game with it. No downgrade template is registered and no
     * downgrade recipe is generated, so nothing in ordinary play reaches this — it exists as the
     * seam the rework will reopen.</p>
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
     * The ladders this item is participating in, whether or not it holds an explicit rung on them.
     *
     * <p>Experience can arrive before any tier is written — a freshly crafted sword earns weapon
     * experience from its first kill and sits at that ladder's initial rung. Those ladders have state
     * recorded but no {@code tier_id}, so they are found by looking at which ladders have a compound
     * at all rather than at which ones name a rung. When the item has recorded nothing yet, the
     * domains its tags claim are used, which is what lets a brand-new item start earning.</p>
     */
    public List<TierDomain> ladderDomains(final ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        // Recorded state is read from the item itself rather than from its tags. An item's tags say
        // which ladders it may join; the compounds it already carries say which ladders it is on,
        // and those two can disagree — a datapack may drop an item from a tag after it has climbed,
        // and a test may install tiers without binding tags at all.
        final List<TierDomain> recorded = TierService.getInstance().recordedLadderDomains(stack);
        // Ladders the item has recorded state on win outright, so an axe that has only ever been
        // used as a tool does not also start climbing the weapon ladder it has never touched.
        // An item that has recorded nothing yet offers no such signal, so every tag it sits in is a
        // ladder it may start on — which is what lets a freshly crafted sword earn experience.
        return recorded.isEmpty()
                ? List.copyOf(TierService.getInstance().inferDomains(stack.getItem()))
                : recorded;
    }

    /**
     * Disabled pending a rework of branching.
     *
     * <p>Always returns false and writes nothing. The shipped ladder is linear, so there is no branch
     * to select in ordinary play; leaving attunement live would mean an off-hand item silently
     * recording a {@code selected_branch_id} that no edge can ever match, which reads as a bug long
     * before the rework lands.</p>
     */
    public boolean attuneStack(final ItemStack mainHand, final ItemStack offhand) {
        return false;
    }

    /**
     * Disabled pending a rework of branching. See {@link #attuneStack(ItemStack, ItemStack)}.
     */
    public boolean attuneStack(final ItemStack mainHand, final ItemStack offhand, final TierDomain domain) {
        return false;
    }

    /**
     * Adds experience to one ladder. Callers gate on the {@code teItemExperience} gamerule, which is
     * per-world, so this only rejects an empty stack, an unknown domain, or a non-positive amount.
     */
    public void addExperience(final ItemStack stack, final TierDomain domain, final int amount) {
        if (stack.isEmpty() || domain == null || amount <= 0) {
            return;
        }
        final int next = this.getStoredExperience(stack, domain) + amount;
        this.setStoredExperience(stack, domain, next);
    }

    public int getStoredExperience(final ItemStack stack, final TierDomain domain) {
        final CompoundTag domainTag = this.readDomainTag(stack, domain);
        return domainTag == null ? 0 : domainTag.getInt("stored_experience");
    }

    public void setStoredExperience(final ItemStack stack, final TierDomain domain, final int amount) {
        if (domain == null) {
            return;
        }
        this.domainTag(stack, domain).putInt("stored_experience", Math.max(0, amount));
    }

    public Optional<String> getSelectedBranchId(final ItemStack stack, final TierDomain domain) {
        final CompoundTag domainTag = this.readDomainTag(stack, domain);
        if (domainTag != null && domainTag.contains("selected_branch_id")) {
            return Optional.of(domainTag.getString("selected_branch_id"));
        }
        return Optional.empty();
    }

    public void clearSelectedBranch(final ItemStack stack, final TierDomain domain) {
        final CompoundTag domainTag = this.readDomainTag(stack, domain);
        if (domainTag == null) {
            return;
        }
        domainTag.remove("selected_branch_id");
        domainTag.remove("selected_branch_target");
        domainTag.remove("selected_branch_source");
    }

    public Optional<ItemStack> tryAutoUpgrade(final ItemStack stack, final MinecraftServer server) {
        return this.tryAutoUpgrade(stack, ProgressionRules.from(server));
    }

    /**
     * Tries every ladder this item holds a rung on, in domain declaration order.
     *
     * <p>Returning a single stack rather than a per-domain result keeps the tick handler simple: an
     * axe that qualifies on both ladders at once advances both, and the action bar reports the item
     * as a whole.</p>
     *
     * <p>The ladders walked are the ones the item is participating in — those it has recorded state
     * on, or its tagged ladders when it has recorded nothing. Walking only ladders that name an
     * explicit rung would strand an item that has earned experience but not yet been tiered, because
     * experience alone writes no {@code tier_id}.</p>
     */
    public Optional<ItemStack> tryAutoUpgrade(final ItemStack stack, final ProgressionRules rules) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ItemStack current = stack;
        boolean upgraded = false;
        for (final TierDomain domain : this.ladderDomains(stack)) {
            final Optional<ItemStack> next = this.tryAutoUpgradeDomain(current, domain, rules);
            if (next.isPresent()) {
                current = next.get();
                upgraded = true;
            }
        }
        return upgraded ? Optional.of(current) : Optional.empty();
    }

    /** Tries one ladder. Returns empty when this domain cannot advance. */
    public Optional<ItemStack> tryAutoUpgradeDomain(final ItemStack stack, final TierDomain domain, final ProgressionRules rules) {
        final TierPolicy policy = PolicyService.getInstance().get();
        if (stack.isEmpty()
                || !rules.itemExperience()
                || !rules.autoUpgrade()
                || !policy.autoUpgradeEnabled()) {
            return Optional.empty();
        }
        final Optional<TierDefinition> current = this.getCurrentTier(stack, domain);
        if (current.isEmpty() || current.get().domain() != domain) {
            return this.tryBootstrap(stack, domain, rules);
        }
        final Optional<String> selectedBranch = this.getSelectedBranchId(stack, domain);
        final List<TierEdge> nextEdges = new ArrayList<>(PathService.getInstance().resolveNextEdges(current.get().id(), domain, selectedBranch));
        if (nextEdges.isEmpty()) {
            return Optional.empty();
        }
        final int currentXp = this.getStoredExperience(stack, domain);
        if (nextEdges.size() == 1) {
            final TierEdge edge = nextEdges.get(0);
            final TierDefinition target = TierService.getInstance().getTier(edge.to(), domain).orElse(null);
            if (target == null) {
                return Optional.empty();
            }
            final int required = edge.effectiveExperienceRequirement(target.experienceRequired());
            if (currentXp < required) {
                return Optional.empty();
            }
            return Optional.of(this.finishUpgrade(stack, target,
                    PathService.getInstance().effectiveBranchId(edge), required, rules));
        }
        final List<TierEdge> eligible = new ArrayList<>();
        for (final TierEdge edge : nextEdges) {
            final TierDefinition target = TierService.getInstance().getTier(edge.to(), domain).orElse(null);
            if (target == null) {
                continue;
            }
            final int required = edge.effectiveExperienceRequirement(target.experienceRequired());
            if (currentXp < required) {
                return Optional.empty();
            }
            eligible.add(edge);
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        final TierEdge chosen = switch (policy.multiPathResolution()) {
            // Deterministic: makes a given item always walk the same branch, which is what a datapack
            // asking for FIRST is after.
            case FIRST -> eligible.get(0);
            case RANDOM -> PathService.getInstance()
                    .chooseRandomEligibleEdge(stack, current.get().id(), eligible)
                    .orElse(eligible.get(0));
        };
        final TierDefinition target = TierService.getInstance().getTier(chosen.to(), domain).orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        final int required = chosen.effectiveExperienceRequirement(target.experienceRequired());
        return Optional.of(this.finishUpgrade(stack, target,
                PathService.getInstance().effectiveBranchId(chosen), required, rules));
    }

    /**
     * The climb from untiered onto the ladder's first rung.
     *
     * <p>Experience reaches an item before any rung is written — a sword banks weapon experience from
     * its first kill and has nothing to spend it on until it is on the ladder. Without this branch
     * that experience would accumulate forever, because there is no edge out of "untiered" for the
     * normal walk to follow. The path graph deliberately says nothing about this step: it describes
     * transitions between rungs, and joining the ladder is not one. So the first rung's own
     * experience requirement is the whole of the gate.</p>
     */
    private Optional<ItemStack> tryBootstrap(final ItemStack stack, final TierDomain domain, final ProgressionRules rules) {
        final TierDefinition target = TierService.getInstance()
                .getOrderedTiers(domain).stream().findFirst().orElse(null);
        if (target == null || target.domain() != domain) {
            return Optional.empty();
        }
        final int required = target.experienceRequired();
        if (this.getStoredExperience(stack, domain) < required) {
            return Optional.empty();
        }
        return Optional.of(this.finishUpgrade(stack, target, TierBootstrapRecipe.UNTIERED.toString(), required, rules));
    }

    private ItemStack finishUpgrade(final ItemStack stack, final TierDefinition target, final String branchId,
                                    final int requiredXp, final ProgressionRules rules) {
        final ItemStack copy = stack.copy();
        final TierDomain domain = target.domain();
        final int currentXp = this.getStoredExperience(copy, domain);
        // teResetExperienceOnUpgrade makes the upgrade spend the whole pool, which is the pacing
        // lever an operator reaches for when automatic progression feels too fast. The datapack's
        // carry_over_excess_xp field is superseded by this: a world is a better place to express
        // pacing than the shared ladder a pack author ships to everyone.
        final int remainder = rules.resetExperienceOnUpgrade()
                ? 0
                : Math.max(0, currentXp - requiredXp);
        this.writeTierState(copy, target, false);
        this.setStoredExperience(copy, domain, remainder);
        this.clearSelectedBranch(copy, domain);
        this.domainTag(copy, domain).putString("last_branch_id", branchId);
        this.refreshArsThreadSlotBonus(copy);
        return copy;
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
            final TierDomain domain = entry.getKey();
            detail.append(" | ").append(domain.getSerializedName())
                    .append(": tier=").append(entry.getValue().id())
                    .append(", xp=").append(this.getStoredExperience(stack, domain))
                    .append(", branch=").append(this.getSelectedBranchId(stack, domain).orElse("none"));
        }
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
        domainTag.putString("display_rarity", tier.displayRarity().name());
        domainTag.putInt("tier_sort_order", tier.sortOrder());
        domainTag.putBoolean("preview", preview);
        domainTag.putBoolean("generated_upgrade_template", tier.generatedUpgradeTemplate());
        domainTag.putBoolean("generated_downgrade_template", tier.generatedDowngradeTemplate());
    }

    /** The compound holding one ladder's state, or null when that ladder is unwritten. */
    private CompoundTag readDomainTag(final ItemStack stack, final TierDomain domain) {
        if (domain == null || stack.isEmpty() || !stack.hasTag()
                || !stack.getTag().contains(TierService.ROOT_TAG)) {
            return null;
        }
        final CompoundTag te = stack.getTag().getCompound(TierService.ROOT_TAG);
        if (!te.contains(TierService.DOMAINS_TAG)) {
            return null;
        }
        final CompoundTag domains = te.getCompound(TierService.DOMAINS_TAG);
        final String key = domain.getSerializedName();
        return domains.contains(key) ? domains.getCompound(key) : null;
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
