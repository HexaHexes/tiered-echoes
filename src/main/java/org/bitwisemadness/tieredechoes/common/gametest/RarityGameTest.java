package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;
import org.bitwisemadness.tieredechoes.common.registry.TEItems;

/**
 * {@code Rarity} cannot be extended in a unit test: appending a constant needs Forge's runtime enum
 * extender, which only exists inside a real game. This is therefore the only place the LEGENDARY
 * rarity can be shown to exist at all, and the only place that can prove it did not come out wrong.
 *
 * <p>Both assertions also cover the reuse path. A mod that extends the enum first leaves
 * {@code TERarities} with its constant rather than ours, and a correct install is indistinguishable
 * from ours by name — which is exactly the point.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class RarityGameTest {

    private RarityGameTest() {
    }

    /**
     * The datapack names this rarity with a string, and every consumer — the reloader, the wire
     * codec, the name above the item — resolves it with {@code valueOf}. If the constant we appended
     * were unreachable by that lookup, the shipped legendary tiers would silently read as COMMON and
     * nothing else in the mod would notice.
     */
    @GameTest
    public static void theLegendaryRarityIsReachableByNameSoADatapackCanNameIt(final GameTestHelper helper) {
        final Rarity legendary = TERarities.legendary()
                .orElseThrow(() -> new GameTestAssertException("LEGENDARY was never registered"));

        helper.assertTrue(Rarity.valueOf(TERarities.LEGENDARY) == legendary,
                "a second LEGENDARY exists, so the name resolves to a rarity that is not ours");

        helper.succeed();
    }

    /**
     * The legendary template texture follows this colour, so it has to be one a hue can be read out
     * of. A rarity declaring no colour, or only a grey one, would leave the plate on golden orange
     * while the name above it said something else — the exact disagreement this feature exists to
     * avoid.
     */
    @GameTest
    public static void theLegendaryRarityDeclaresAColourTheTemplateCanFollow(final GameTestHelper helper) {
        final Rarity legendary = TERarities.legendary()
                .orElseThrow(() -> new GameTestAssertException("LEGENDARY was never registered"));
        final int colour = TERarities.colourOf(legendary)
                .orElseThrow(() -> new GameTestAssertException("LEGENDARY declares no colour at all"));

        helper.assertTrue(saturation(colour) > 0,
                String.format("LEGENDARY's colour #%06X is unsaturated, so the template has no hue to follow",
                        colour));

        helper.succeed();
    }

    /**
     * The full chain for the one rarity that is not a vanilla constant: the shipped datapack names
     * {@code LEGENDARY}, the upgrade service writes that name into the item's NBT, and the rarity
     * mixin resolves it back out at render time. Every link is separately covered elsewhere; this
     * is the only test that exercises them end to end, and it is the one that would notice if
     * {@code Rarity.valueOf} ever stopped seeing runtime-appended constants.
     */
    @GameTest
    public static void aLegendaryItemResolvesTheExtendedRarityFromItsTier(final GameTestHelper helper) {
        final Rarity legendary = TERarities.legendary()
                .orElseThrow(() -> new GameTestAssertException("LEGENDARY was never registered"));
        final ItemStack stack = GameTestTiers.tieredSword(helper, "legendary");

        helper.assertTrue(stack.getRarity() == legendary,
                "expected the LEGENDARY constant but got " + stack.getRarity());

        helper.succeed();
    }

    /**
     * The template item declares its rarity once, when it is registered, rather than reading it
     * back out of NBT the way a tiered item does — so it fails through a different route and needs
     * its own assertion. It reached {@code COMMON} by falling through the tier lookup, which no
     * other test could see: the rarity itself registered fine, and a tiered item of the same tier
     * still rendered golden.
     */
    @GameTest
    public static void theLegendaryTemplateIsRegisteredAsTheLegendaryRarity(final GameTestHelper helper) {
        final Rarity legendary = TERarities.legendary()
                .orElseThrow(() -> new GameTestAssertException("LEGENDARY was never registered"));
        final Item template = TEItems.UPGRADE_TEMPLATES.get("legendary").get();
        final Rarity declared = template.getDefaultInstance().getRarity();

        helper.assertTrue(declared == legendary,
                "the legendary template declares " + declared + ", not LEGENDARY");

        helper.succeed();
    }

    /** Chromatic spread in bytes, which is saturation in every system that would call it that. */
    private static int saturation(final int colour) {
        final int red = (colour >> 16) & 0xFF;
        final int green = (colour >> 8) & 0xFF;
        final int blue = colour & 0xFF;
        return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
    }
}
