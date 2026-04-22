package org.bitwisemadness.tieredechoes.common.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackRarityMixin {

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void tieredechoes$overrideRarity(CallbackInfoReturnable<Rarity> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.hasTag()) {
            return;
        }

        CompoundTag root = self.getTag();
        if (root == null || !root.contains("TieredEchoes")) {
            return;
        }

        CompoundTag te = root.getCompound("TieredEchoes");
        if (!te.contains("display_rarity")) {
            return;
        }

        String rarityName = te.getString("display_rarity");
        try {
            cir.setReturnValue(Rarity.valueOf(rarityName));
        } catch (IllegalArgumentException ignored) {
        }
    }
}
