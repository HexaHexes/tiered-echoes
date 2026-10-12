package org.bitwisemadness.tieredechoes.common.rarity;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.TieredEchoes;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

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
     * Creates the constant, unless one already exists. Idempotent, so a caller that is unsure of the
     * phase can call it freely.
     *
     * <p>Called from the mod constructor rather than common setup, because item registration runs
     * earlier than common setup and the template items want this rarity the moment they are built.
     * Common setup would leave the legendary template holding the wrong colour, or force a lookup
     * that cannot succeed yet.</p>
     *
     * <p>The existence probe matters because another mod can reach this enum first. Appending a
     * second {@code LEGENDARY} would leave two constants answering to one name — {@code valueOf}
     * returns whichever was added first, so ours would be unreachable by name and its colour would
     * apply to nothing at all. Reusing theirs is also the only outcome a player would recognise as
     * correct: two mods disagreeing about what "legendary" looks like is a bug either way, and the
     * one that got there first owns the word.</p>
     *
     * <p>This cannot help the other direction. A mod extending the enum after us appends its own
     * constant regardless, and there is no registry to arbitrate; that is Forge's problem rather
     * than ours to solve from here.</p>
     */
    public static synchronized void register() {
        if (legendary != null) {
            return;
        }
        final Rarity existing = findExisting(LEGENDARY);
        if (existing != null) {
            legendary = existing;
            final int reusedColour = colourOf(existing).orElse(-1);
            TieredEchoes.LOGGER.info(
                    "[TieredEchoes] Rarity {} was already added by another mod; reusing it instead of adding a "
                            + "second one. Legendary items take that rarity's colour{}.",
                    LEGENDARY, reusedColour < 0 ? "" : String.format(Locale.ROOT, " #%06X", reusedColour));
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
     * The named constant, or null when the enum has no such name.
     *
     * <p>{@code valueOf} is the right probe rather than scanning {@code values()}: it resolves
     * constants Forge appended at runtime. The GameTest server shows this working — it loads
     * {@code display_rarity: "LEGENDARY"} out of the shipped datapack with no "unknown rarity"
     * warning, which could only happen if {@code valueOf} saw past the vanilla four. It is also the
     * same lookup every other caller already uses, so a name that passes here resolves everywhere:
     * the reloader, the wire codec and the tooltip alike.</p>
     */
    static Rarity findExisting(final String name) {
        try {
            return Rarity.valueOf(name);
        } catch (final IllegalArgumentException absent) {
            return null;
        }
    }

    /**
     * The rarity's real colour as {@code 0xRRGGBB}, or empty when it declares none.
     *
     * <p>Read through the style modifier rather than the deprecated {@code color} field, and that
     * is not a matter of taste. Forge's extension hands every appended constant the
     * {@code Rarity(UnaryOperator)} constructor, which hardcodes the field to {@code BLACK} — so
     * asking another mod's {@code LEGENDARY} for its colour through the field reports black. The
     * style modifier is what {@code ItemStack#getTooltipLines} applies, so reading it here reports
     * the colour a player actually sees.</p>
     */
    public static OptionalInt colourOf(final Rarity rarity) {
        if (rarity == null) {
            return OptionalInt.empty();
        }
        final TextColor colour = rarity.getStyleModifier().apply(Style.EMPTY).getColor();
        return colour == null ? OptionalInt.empty() : OptionalInt.of(colour.getValue());
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
        final Rarity resolved = findExisting(name.trim().toUpperCase(Locale.ROOT));
        if (resolved == null) {
            TieredEchoes.LOGGER.warn("[TieredEchoes] Unknown rarity {}; reading it as {}", name, fallback);
            return fallback;
        }
        return resolved;
    }
}
