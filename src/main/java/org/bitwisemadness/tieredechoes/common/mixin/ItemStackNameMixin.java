package org.bitwisemadness.tieredechoes.common.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.HighestRung;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Appends the {@code [Tier]} suffix to an item's display name at render time.
 *
 * <p>Composing the name lazily instead of writing {@code display.Name} is what allows a smithing
 * material upgrade (which copies the base stack's NBT verbatim) to keep its tier without also
 * freezing the previous material's name — and without baking one language's translation into NBT.</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackNameMixin {

    /**
     * Tier names written by earlier builds of this mod into {@code display.Name}. Only consulted when
     * a custom name is actually present, so ordinary items pay a single NBT lookup.
     */
    private static final Set<String> LEGACY_TIER_SUFFIXES =
            Set.of("common", "uncommon", "rare", "epic", "mythic", "legendary");

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void tieredechoes$appendTierSuffix(final CallbackInfoReturnable<Component> cir) {
        final ItemStack self = (ItemStack) (Object) this;
        final CompoundTag root = self.getTag();
        if (root == null || !root.contains(TierService.ROOT_TAG)) {
            return;
        }
        final CompoundTag te = root.getCompound(TierService.ROOT_TAG);
        if (!te.contains(TierService.DOMAINS_TAG)) {
            return;
        }
        final Optional<CompoundTag> rung = HighestRung.of(te.getCompound(TierService.DOMAINS_TAG));
        if (rung.isEmpty()) {
            return;
        }
        final ResourceLocation parsed = ResourceLocation.tryParse(rung.get().getString(TierService.TIER_ID_TAG));
        if (parsed == null) {
            return;
        }

        final String tierName = capitalize(parsed.getPath());
        final String suffix = " [" + tierName + "]";
        final Component base = cir.getReturnValue();
        final String text = base.getString();
        if (text.endsWith(suffix)) {
            return;
        }
        final String cleaned = stripLegacyTierSuffix(self, text);
        cir.setReturnValue(Component.literal(cleaned).append(suffix).withStyle(base.getStyle()));
    }

    /**
     * Removes a stale {@code [Tier]} suffix left in NBT by builds that baked the name, so legacy items
     * do not render {@code [Common] [Rare]}. A suffix is only removed when it is literally a known
     * tier name, so ordinary player renames such as {@code [PvP]} are preserved.
     */
    private static String stripLegacyTierSuffix(final ItemStack stack, final String text) {
        if (!stack.hasCustomHoverName()) {
            return text;
        }
        final int open = text.lastIndexOf(" [");
        if (open < 0 || !text.endsWith("]")) {
            return text;
        }
        final String inner = text.substring(open + 2, text.length() - 1).trim().toLowerCase(Locale.ROOT);
        if (!LEGACY_TIER_SUFFIXES.contains(inner)) {
            return text;
        }
        return text.substring(0, open);
    }

    private static String capitalize(final String raw) {
        final StringBuilder builder = new StringBuilder(raw.length());
        for (final String part : raw.replace('_', ' ').split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }
}
