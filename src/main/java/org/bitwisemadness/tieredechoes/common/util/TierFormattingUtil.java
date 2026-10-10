package org.bitwisemadness.tieredechoes.common.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;

import java.util.Locale;

public final class TierFormattingUtil {
    private TierFormattingUtil() {}

    public static String capitalizeTierName(final TierDefinition tier) {
        final String raw = tier.id().getPath().replace('_', ' ');
        return toTitleCase(raw);
    }

    public static String toTitleCase(final String input) {
        final String[] parts = input.split(" ");
        final StringBuilder builder = new StringBuilder();
        for (final String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(part.substring(0,1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) builder.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    public static Component buildTieredName(final ItemStack stack, final TierDefinition tier) {
        String baseName = stack.getHoverName().getString();
        final String suffixPattern = " \\[.*\\]$";
        if (baseName.matches(".*" + suffixPattern)) {
            baseName = baseName.replaceFirst(suffixPattern, "");
        }
        if (baseName.isBlank()) {
            baseName = stack.getItem().getDescription().getString();
        }
        return Component.literal(baseName + " [" + capitalizeTierName(tier) + "]").withStyle(styleForRarity(tier.displayRarity()));
    }

    public static ChatFormatting styleForRarity(final Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> ChatFormatting.YELLOW;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case COMMON -> ChatFormatting.WHITE;
        };
    }

}
