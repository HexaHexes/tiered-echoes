package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

/**
 * How a tiered item renders: the {@code [Tier]} suffix is appended by a mixin on
 * {@code ItemStack#getHoverName}, and the name's colour comes from a second mixin on
 * {@code ItemStack#getRarity}. Mixins never run in a plain JVM, so this is the only place either
 * behaviour can actually be observed.
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class DisplayNameGameTest {

    private DisplayNameGameTest() {
    }

    @GameTest
    public static void aTieredItemSaysWhichTierItIsOn(final GameTestHelper helper) {
        final ItemStack stack = GameTestTiers.tieredSword(helper, "uncommon");

        helper.assertTrue(stack.getHoverName().getString().endsWith(" [Uncommon]"),
                "expected a tier suffix but got \"" + stack.getHoverName().getString() + "\"");
        helper.succeed();
    }

    /**
     * The suffix and the colour have to name the same rung. A mod-registered item is {@code COMMON}
     * unless something rewrites that, so without the rarity mixin an epic sword would render its
     * name in plain white however high it had climbed.
     */
    @GameTest
    public static void aTieredItemCarriesTheRarityItsTierDeclares(final GameTestHelper helper) {
        final ItemStack stack = GameTestTiers.tieredSword(helper, "uncommon");

        helper.assertTrue(stack.getRarity() == Rarity.UNCOMMON,
                "expected the uncommon tier's rarity but got " + stack.getRarity());
        helper.succeed();
    }

    @GameTest
    public static void anUntieredItemsRarityIsLeftAlone(final GameTestHelper helper) {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);

        helper.assertTrue(stack.getRarity() == Rarity.COMMON,
                "an item with no TieredEchoes tag must keep the rarity its item declares");
        helper.succeed();
    }

    @GameTest
    public static void anUntieredItemsNameIsLeftAlone(final GameTestHelper helper) {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);

        helper.assertFalse(stack.getHoverName().getString().contains("["),
                "an item with no TieredEchoes tag must not grow a suffix");
        helper.succeed();
    }

    /**
     * Older builds baked the suffix into {@code display.Name}. Someone carrying one of those must
     * see the current tier exactly once, not the baked tier and the current one stacked up.
     */
    @GameTest
    public static void aLegacyBakedSuffixIsReplacedRatherThanStacked(final GameTestHelper helper) {
        final ItemStack renamed = new ItemStack(Items.IRON_SWORD);
        renamed.setHoverName(Component.literal("Excalibur [Rare]"));
        final ItemStack stack = UpgradeService.getInstance()
                .applyTier(renamed, GameTestTiers.tier(helper, TierDomain.WEAPON, "uncommon"));

        helper.assertTrue(stack.getHoverName().getString().equals("Excalibur [Uncommon]"),
                "expected the stale suffix to be swapped out but got \"" + stack.getHoverName().getString() + "\"");
        helper.succeed();
    }

    /** Only tier names may be stripped, so a rename like {@code [PvP]} has to survive untouched. */
    @GameTest
    public static void aPlayersOwnBracketedRenameIsNotMistakenForALegacyTier(final GameTestHelper helper) {
        final ItemStack renamed = new ItemStack(Items.IRON_SWORD);
        renamed.setHoverName(Component.literal("Blade [PvP]"));
        final ItemStack stack = UpgradeService.getInstance()
                .applyTier(renamed, GameTestTiers.tier(helper, TierDomain.WEAPON, "uncommon"));

        helper.assertTrue(stack.getHoverName().getString().equals("Blade [PvP] [Uncommon]"),
                "expected the player's own brackets to be kept but got \"" + stack.getHoverName().getString() + "\"");
        helper.succeed();
    }
}
