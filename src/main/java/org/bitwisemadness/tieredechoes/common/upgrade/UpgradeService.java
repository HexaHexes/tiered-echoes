package org.bitwisemadness.tieredechoes.common.upgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.api.common.IUpgradeService;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
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
        writeTierState(copy, tier, true);
        return copy;
    }

    @Override
    public ItemStack applyTier(final ItemStack stack, final TierDefinition tier) {
        final ItemStack copy = stack.copy();
        final int currentXp = getStoredExperience(copy);
        writeTierState(copy, tier, false);
        setStoredExperience(copy, currentXp);
        return copy;
    }

    @Override
    public ItemStack downgrade(final ItemStack stack) {
        final Optional<TierDefinition> current = getCurrentTier(stack);
        if (current.isEmpty()) {
            return stack.copy();
        }
        final List<TierEdge> incoming = PathService.getInstance().getIncomingEdges(current.get().id());
        final Optional<String> selectedBranch = getSelectedBranchId(stack);
        TierEdge chosen = null;
        if (selectedBranch.isPresent()) {
            chosen = incoming.stream().filter(edge -> PathService.getInstance().effectiveBranchId(edge).equals(selectedBranch.get())).min(Comparator.comparingInt(TierEdge::registrationOrder)).orElse(null);
        }
        if (chosen == null) {
            chosen = incoming.stream().min(Comparator.comparingInt(TierEdge::registrationOrder)).orElse(null);
        }
        if (chosen == null) {
            return stack.copy();
        }
        final Optional<TierDefinition> previous = TierService.getInstance().getTier(chosen.downgradeTo().orElse(chosen.from()));
        if (previous.isEmpty()) {
            return stack.copy();
        }
        final ItemStack copy = stack.copy();
        final int xp = getStoredExperience(copy);
        writeTierState(copy, previous.get(), false);
        setStoredExperience(copy, xp);
        clearSelectedBranch(copy);
        return copy;
    }

    public Optional<TierDefinition> getCurrentTier(final ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TE_TAG)) {
            final CompoundTag te = stack.getTag().getCompound(TE_TAG);
            if (te.contains("tier_id")) {
                return TierService.getInstance().getTier(ResourceLocation.tryParse(te.getString("tier_id")));
            }
        }
        final var domains = TierService.getInstance().inferDomains(stack.getItem());
        if (domains.isEmpty()) {
            return Optional.empty();
        }
        final var preferred = domains.iterator().next();
        return TierService.getInstance().getInitialTierForItem(stack.getItem(), preferred);
    }

    public boolean attuneStack(final ItemStack mainHand, final ItemStack offhand) {
        final Optional<TierDefinition> current = this.getCurrentTier(mainHand);
        if (current.isEmpty()) {
            return false;
        }
        final Optional<TierEdge> edge = PathService.getInstance().resolveAttunementEdge(current.get().id(), offhand);
        if (edge.isEmpty()) {
            return false;
        }
        final CompoundTag te = mainHand.getOrCreateTagElement(TE_TAG);
        te.putString("selected_branch_id", PathService.getInstance().effectiveBranchId(edge.get()));
        te.putString("selected_branch_target", edge.get().to().toString());
        te.putString("selected_branch_source", current.get().id().toString());
        return true;
    }

    public void addExperience(final ItemStack stack, final int amount) {
        if (stack.isEmpty() || amount <= 0 || !TieredEchoesConfig.ENABLE_ITEM_XP.get()) {
            return;
        }
        final int next = getStoredExperience(stack) + amount;
        setStoredExperience(stack, next);
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

    public Optional<ItemStack> tryAutoUpgrade(final ItemStack stack) {
        if (stack.isEmpty() || !TieredEchoesConfig.ENABLE_ITEM_XP.get()) {
            return Optional.empty();
        }
        final Optional<TierDefinition> current = getCurrentTier(stack);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        final Optional<TierDomain> preferredDomain = TierService.getInstance().inferDomains(stack.getItem()).stream().findFirst();
        if (preferredDomain.isEmpty()) {
            return Optional.empty();
        }
        final List<TierEdge> nextEdges = new ArrayList<>(PathService.getInstance().resolveNextEdges(current.get().id(), getSelectedBranchId(stack)));
        if (nextEdges.isEmpty()) {
            return Optional.empty();
        }
        final int currentXp = getStoredExperience(stack);
        if (nextEdges.size() == 1) {
            final TierEdge edge = nextEdges.get(0);
            final TierDefinition target = TierService.getInstance().getTier(edge.to()).orElse(null);
            if (target == null) {
                return Optional.empty();
            }
            final int required = edge.effectiveExperienceRequirement(target.experienceRequired());
            if (currentXp < required) {
                return Optional.empty();
            }
            return Optional.of(finishUpgrade(stack, target, edge, required));
        }
        if (!TieredEchoesConfig.ENABLE_AUTO_UPGRADE_SINGLE_PATH.get() && getSelectedBranchId(stack).isEmpty()) {
            return Optional.empty();
        }
        final List<TierEdge> eligible = new ArrayList<>();
        for (final TierEdge edge : nextEdges) {
            final TierDefinition target = TierService.getInstance().getTier(edge.to()).orElse(null);
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
        final TierEdge chosen = PathService.getInstance().chooseRandomEligibleEdge(stack, current.get().id(), eligible).orElse(eligible.get(0));
        final TierDefinition target = TierService.getInstance().getTier(chosen.to()).orElse(null);
        if (target == null) {
            return Optional.empty();
        }
        final int required = chosen.effectiveExperienceRequirement(target.experienceRequired());
        return Optional.of(finishUpgrade(stack, target, chosen, required));
    }

    private ItemStack finishUpgrade(final ItemStack stack, final TierDefinition target, final TierEdge chosen, final int requiredXp) {
        final ItemStack copy = stack.copy();
        final int remainder = Math.max(0, getStoredExperience(copy) - requiredXp);
        writeTierState(copy, target, false);
        setStoredExperience(copy, remainder);
        clearSelectedBranch(copy);
        final CompoundTag te = copy.getOrCreateTagElement(TE_TAG);
        te.putString("last_branch_id", PathService.getInstance().effectiveBranchId(chosen));
        return copy;
    }

    public Component describeStack(final ItemStack stack) {
        final Optional<TierDefinition> tier = getCurrentTier(stack);
        final Component base = Component.literal(stack.getHoverName().getString());
        if (tier.isEmpty()) {
            return base;
        }
        final String branch = getSelectedBranchId(stack).orElse("none");
        return Component.literal(base.getString() + " | tier=" + tier.get().id() + " | xp=" + getStoredExperience(stack) + " | branch=" + branch).withStyle(ChatFormatting.GRAY);
    }

    private static void writeTierState(final ItemStack stack, final TierDefinition tier, final boolean preview) {
        final CompoundTag te = stack.getOrCreateTagElement(TE_TAG);
        te.putString("tier_id", tier.id().toString());
        te.putString("tier_domain", tier.domain().getSerializedName());
        te.putInt("schema_version", tier.schemaVersion());
        te.putString("display_rarity", tier.displayRarity().name());
        te.putInt("tier_sort_order", tier.sortOrder());
        te.putBoolean("preview", preview);
        te.putBoolean("generated_upgrade_template", tier.generatedUpgradeTemplate());
        te.putBoolean("generated_downgrade_template", tier.generatedDowngradeTemplate());
    }
}
