package org.bitwisemadness.tieredechoes.common.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;
import org.bitwisemadness.tieredechoes.common.tier.HighestRung;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a tiered item the rarity its tier declares, so the name is painted with the tier's colour.
 *
 * <p>{@code ItemStack#getTooltipLines} wraps the hover name in
 * {@code Component.empty().append(name).withStyle(getRarity().getStyleModifier())}, so the name's
 * colour comes from {@code getRarity()} and from nowhere else. A mod-registered item is
 * {@code COMMON} unless something says otherwise, which means an epic sword would render in plain
 * white unless this mixin rewrites the answer to the tier's rarity.</p>
 *
 * <p>The rarity is read from the same per-domain compound that {@code writeTierState} writes it
 * to, and the same rung is chosen as for the {@code [Tier]} suffix — {@link HighestRung} decides
 * once so a name can never claim one tier while its colour says another.</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackRarityMixin {

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void tieredechoes$overrideRarity(final CallbackInfoReturnable<Rarity> cir) {
        final ItemStack self = (ItemStack) (Object) this;
        final CompoundTag root = self.getTag();
        if (root == null || !root.contains(TierService.ROOT_TAG)) {
            return;
        }
        final CompoundTag te = root.getCompound(TierService.ROOT_TAG);
        if (!te.contains(TierService.DOMAINS_TAG)) {
            return;
        }
        final CompoundTag rung = HighestRung.of(te.getCompound(TierService.DOMAINS_TAG)).orElse(null);
        if (rung == null) {
            return;
        }
        // An unknown name falls back rather than throwing: this is the render path for every item
        // tooltip, and a datapack typo should cost the colour, not the frame.
        cir.setReturnValue(TERarities.byName(rung.getString(TierService.DISPLAY_RARITY_TAG), Rarity.COMMON));
    }
}
