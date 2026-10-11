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
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class UpgradeService implements IUpgradeService {
    private static final UpgradeService INSTANCE = new UpgradeService();
    private static final String TE_TAG = "TieredEchoes";

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
     * Applies a tier while preserving stored experience, and consumes any pending branch selection —
     * the branch belonged to the transition that was just taken.
     */
    @Override
    public ItemStack applyTier(final ItemStack stack, final TierDefinition tier) {
        final ItemStack copy = stack.copy();
        final int currentXp = this.getStoredExperience(copy);
        this.writeTierState(copy, tier, false);
        this.setStoredExperience(copy, currentXp);
        this.clearSelectedBranch(copy);
        return copy;
    }

    @Override
    public ItemStack downgrade(final ItemStack stack) {
        final Optional<TierDefinition> current = this.getCurrentTier(stack);
        if (current.isEmpty()) {
            return stack.copy();
        }
        final TierDomain domain = current.get().domain();
        final List<TierEdge> incoming = PathService.getInstance().getIncomingEdges(current.get().id(), domain);
        final Optional<String> selectedBranch = this.getSelectedBranchId(stack);
        TierEdge chosen = null;
        if (selectedBranch.isPresent()) {
            chosen = incoming.stream()
                    .filter(edge -> PathService.getInstance().effectiveBranchId(edge).equals(selectedBranch.get()))
                    .min(Comparator.comparingInt(TierEdge::registrationOrder))
                    .orElse(null);
        }
        if (chosen == null) {
            chosen = incoming.stream().min(Comparator.comparingInt(TierEdge::registrationOrder)).orElse(null);
        }
        if (chosen == null) {
            return stack.copy();
        }
        final Optional<TierDefinition> previous = TierService.getInstance().getTier(chosen.downgradeTo().orElse(chosen.from()), domain);
        if (previous.isEmpty()) {
            return stack.copy();
        }
        final ItemStack copy = stack.copy();
        final int xp = this.getStoredExperience(copy);
        this.writeTierState(copy, previous.get(), false);
        this.setStoredExperience(copy, xp);
        this.clearSelectedBranch(copy);
        return copy;
    }

    public Optional<TierDefinition> getCurrentTier(final ItemStack stack) {
        return TierService.getInstance().resolveCurrentTier(stack, null);
    }

    public boolean attuneStack(final ItemStack mainHand, final ItemStack offhand) {
        final Optional<TierDefinition> current = this.getCurrentTier(mainHand);
        if (current.isEmpty()) {
            return false;
        }
        final Optional<TierEdge> edge = PathService.getInstance()
                .resolveAttunementEdge(current.get().id(), current.get().domain(), offhand);
        if (edge.isEmpty()) {
            return false;
        }
        final CompoundTag te = mainHand.getOrCreateTagElement(TE_TAG);
        te.putString("selected_branch_id", PathService.getInstance().effectiveBranchId(edge.get()));
        te.putString("selected_branch_target", edge.get().to().toString());
        te.putString("selected_branch_source", current.get().id().toString());
        return true;
    }

    /**
     * Adds experience to a stack. Callers gate on the {@code teItemExperience} gamerule, which is
     * per-world, so this only rejects an empty stack or a non-positive amount.
     */
    public void addExperience(final ItemStack stack, final int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        final int next = this.getStoredExperience(stack) + amount;
        this.setStoredExperience(stack, next);
    }

    public int getStoredExperience(final ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TE_TAG)) {
            return 0;
        }
        return stack.getTag().getCompound(TE_TAG).getInt("stored_experience");
    }

    public void setStoredExperience(final ItemStack stack, final int amount) {
        final CompoundTag te = stack.getOrCreateTagElement(TE_TAG);
        te.putInt("stored_experience", Math.max(0, amount));
    }

    public Optional<String> getSelectedBranchId(final ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TE_TAG)) {
            final CompoundTag te = stack.getTag().getCompound(TE_TAG);
            if (te.contains("selected_branch_id")) {
                return Optional.of(te.getString("selected_branch_id"));
            }
        }
        return Optional.empty();
    }

    public void clearSelectedBranch(final ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TE_TAG)) {
            return;
        }
        final CompoundTag te = stack.getTag().getCompound(TE_TAG);
        te.remove("selected_branch_id");
        te.remove("selected_branch_target");
        te.remove("selected_branch_source");
    }

    public Optional<ItemStack> tryAutoUpgrade(final ItemStack stack, final MinecraftServer server) {
        return this.tryAutoUpgrade(stack, ProgressionRules.from(server));
    }

    public Optional<ItemStack> tryAutoUpgrade(final ItemStack stack, final ProgressionRules rules) {
        final TierPolicy policy = PolicyService.getInstance().get();
        if (stack.isEmpty()
                || !rules.itemExperience()
                || !rules.autoUpgrade()
                || !policy.autoUpgradeEnabled()) {
            return Optional.empty();
        }
        final Optional<TierDefinition> current = this.getCurrentTier(stack);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        final TierDomain domain = current.get().domain();
        final Optional<String> selectedBranch = this.getSelectedBranchId(stack);
        final List<TierEdge> nextEdges = new ArrayList<>(PathService.getInstance().resolveNextEdges(current.get().id(), domain, selectedBranch));
        if (nextEdges.isEmpty()) {
            return Optional.empty();
        }
        final int currentXp = this.getStoredExperience(stack);
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
            return Optional.of(this.finishUpgrade(stack, target, edge, required, rules));
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
        return Optional.of(this.finishUpgrade(stack, target, chosen, required, rules));
    }

    private ItemStack finishUpgrade(final ItemStack stack, final TierDefinition target,
                                    final TierEdge chosen, final int requiredXp, final ProgressionRules rules) {
        final ItemStack copy = stack.copy();
        final int currentXp = this.getStoredExperience(copy);
        // teResetExperienceOnUpgrade makes the upgrade spend the whole pool, which is the pacing
        // lever an operator reaches for when automatic progression feels too fast. The datapack's
        // carry_over_excess_xp field is superseded by this: a world is a better place to express
        // pacing than the shared ladder a pack author ships to everyone.
        final int remainder = rules.resetExperienceOnUpgrade()
                ? 0
                : Math.max(0, currentXp - requiredXp);
        this.writeTierState(copy, target, false);
        this.setStoredExperience(copy, remainder);
        this.clearSelectedBranch(copy);
        final CompoundTag te = copy.getOrCreateTagElement(TE_TAG);
        te.putString("last_branch_id", PathService.getInstance().effectiveBranchId(chosen));
        return copy;
    }

    public Component describeStack(final ItemStack stack) {
        final Optional<TierDefinition> tier = this.getCurrentTier(stack);
        // Item#getName is used rather than ItemStack#getHoverName so the diagnostic line does not
        // repeat the [Tier] suffix that the name mixin already appends.
        final String baseName = stack.getItem().getName(stack).getString();
        if (tier.isEmpty()) {
            return Component.literal(baseName).withStyle(ChatFormatting.GRAY);
        }
        final String branch = this.getSelectedBranchId(stack).orElse("none");
        return Component.literal(baseName + " | tier=" + tier.get().id() + " | xp=" + this.getStoredExperience(stack)
                + " | branch=" + branch).withStyle(ChatFormatting.GRAY);
    }

    private static void applyArsThreadSlotState(final ItemStack stack, final TierDefinition tier, final CompoundTag te) {
        final ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"ars_nouveau".equals(itemId.getNamespace()) || !(stack.getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            te.remove("ars_thread_slot_tier_bonus");
            return;
        }
        final int slotTierBonus = Math.max(0, Math.min(3, tier.sortOrder()));
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
        final CompoundTag te = stack.getOrCreateTagElement(TE_TAG);
        te.putString("tier_id", tier.id().toString());
        te.putString("tier_domain", tier.domain().getSerializedName());
        te.putInt("schema_version", tier.schemaVersion());
        te.putString("display_rarity", tier.displayRarity().name());
        te.putInt("tier_sort_order", tier.sortOrder());
        te.putBoolean("preview", preview);
        te.putBoolean("generated_upgrade_template", tier.generatedUpgradeTemplate());
        te.putBoolean("generated_downgrade_template", tier.generatedDowngradeTemplate());
        applyArsThreadSlotState(stack, tier, te);
    }
}
