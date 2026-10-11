package org.bitwisemadness.tieredechoes.common.rarity;

import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.TieredEchoes;

import java.util.Locale;
import java.util.Optional;

/**
 * Adds a {@code LEGENDARY} rarity above {@code EPIC}.
 *
 * <p>Vanilla {@code Rarity} is a plain Java enum with four constants and no registry, so a new rarity
 * cannot be registered the way an item can. Forge patches the enum to implement
 * {@code IExtensibleEnum}, which ASM-rewrites the {@code create} factory to append a real constant to
 * the enum's backing array — and, crucially, patches {@code ItemStack#getTooltipLines} to paint the
 * name with {@code rarity.getStyleModifier()} rather than the constant's fixed colour field. That
 * second patch is what makes a golden-orange rarity possible at all: the colour is a
 * {@code UnaryOperator<Style>} supplied here, not a value the enum hardcodes.</p>
 *
 * <p>{@code LEGENDARY} therefore does not exist at compile time. Nothing may write
 * {@code Rarity.LEGENDARY}, because the source enum has no such constant; callers hold the resolved
 * value from {@link #legendary()} or go through {@link #byName(String, Rarity)}. Datapacks are
 * unaffected — {@code Rarity.valueOf("LEGENDARY")} resolves once the constant exists, because Forge's
 * extension rewrites the array {@code valueOf} reads.</p>
 */
public final class TERarities {

    /** The enum constant name written to NBT and accepted in {@code display_rarity}. */
    public static final String LEGENDARY = "LEGENDARY";

    /**
     * Golden orange: full red, two-thirds green, no blue. Deliberately not a {@link
     * net.minecraft.ChatFormatting} value, because none of the sixteen vanilla formats lands between
     * {@code GOLD} (which is yellow) and {@code RED}.
     */
    public static final int LEGENDARY_COLOR = 0xFFAA00;

    private static Rarity legendary;

    private TERarities() {
    }

    /**
     * Creates the constant. Idempotent, so a caller that is unsure of the phase can call it freely.
     *
     * <p>Called from the mod constructor rather than common setup, because item registration runs
     * earlier than common setup and the template items want this rarity the moment they are built.
     * Common setup would leave the legendary template holding the wrong colour, or force a lookup
     * that cannot succeed yet.</p>
     */
    public static synchronized void register() {
        if (legendary != null) {
            return;
        }
        try {
            legendary = Rarity.create(LEGENDARY, style -> style.withColor(LEGENDARY_COLOR));
        } catch (final RuntimeException exception) {
            // A failed extension is not worth failing the game over — the rarity simply reads as the
            // fallback, and every tier that asked for it still loads.
            TieredEchoes.LOGGER.warn("[TieredEchoes] Could not register the {} rarity", LEGENDARY, exception);
        }
    }

    public static Optional<Rarity> legendary() {
        return Optional.ofNullable(legendary);
    }

    /**
     * Resolves a rarity by name, falling back rather than throwing.
     *
     * <p>Datapacks are written by hand, so an unrecognised {@code display_rarity} is a mistake a
     * player should not be told about in the form of a hard crash during reload. Anything that fails
     * to resolve reads as the fallback, and the rest of the tier still loads.</p>
     */
    public static Rarity byName(final String name, final Rarity fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            TieredEchoes.LOGGER.warn("[TieredEchoes] Unknown rarity {}; reading it as {}", name, fallback);
            return fallback;
        }
    }
}
