package org.bitwisemadness.tieredechoes.common.mixin;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales a stack's maximum durability by its tier.
 *
 * <p>Applied at the <em>read</em> site rather than written into the stack, which is the whole point:
 * Forge routes {@code ItemStack#getMaxDamage()} through {@code Item#getMaxDamage(ItemStack)}, so
 * every other mod's contribution to durability is already in the number that arrives here. Scaling
 * that number composes. Writing a scaled maximum instead would overwrite whatever anyone else had
 * contributed, and the last writer would win silently.</p>
 *
 * <p>A multiplier of {@code 1.0} — including the common case of a tier that declares no durability
 * key at all — returns the original value untouched, so untiered items pay one NBT lookup and no
 * arithmetic.</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {

    /**
     * Guards against re-entry. {@code getMaxDamage()} sits under {@code isDamageableItem()} and
     * {@code hurt()}, both of which are reachable from the tier lookup path, so an unguarded mixin
     * can recurse until the stack overflows.
     */
    @Unique
    private static boolean tieredechoes$scaling;

    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void tieredechoes$scaleDurability(final CallbackInfoReturnable<Integer> cir) {
        if (tieredechoes$scaling) {
            return;
        }
        final ItemStack self = (ItemStack) (Object) this;
        final int base = cir.getReturnValue();
        if (base <= 0) {
            return;
        }
        tieredechoes$scaling = true;
        try {
            final Optional<Double> multiplier = tieredechoes$durabilityMultiplier(self);
            if (multiplier.isEmpty() || multiplier.get() == 1.0D) {
                return;
            }
            final int scaled = (int) Math.round(base * multiplier.get());
            if (scaled != base) {
                cir.setReturnValue(Math.max(1, scaled));
            }
        } catch (RuntimeException exception) {
            // A scaling failure must never break the read site, because everything that asks about
            // durability goes through here. Fall back to the unscaled value and say so.
            TieredEchoes.LOGGER.warn("[TieredEchoes] Could not scale durability for {}", self.getItem(), exception);
        } finally {
            tieredechoes$scaling = false;
        }
    }

    @Unique
    private static Optional<Double> tieredechoes$durabilityMultiplier(final ItemStack stack) {
        // All ladders are consulted and the strongest multiplier wins, because an axe climbs both
        // the weapon and the tool ladder and either may state a durability multiplier. Picking one
        // arbitrarily would make the result depend on iteration order rather than on the data.
        return TierService.getInstance().getScalarMultiplier(stack, "durability");
    }
}
