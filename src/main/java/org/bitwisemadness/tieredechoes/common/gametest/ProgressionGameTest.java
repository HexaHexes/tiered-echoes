package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.util.Optional;

/**
 * Progression driven by the ladder, config and policy that actually shipped — the unit tests drive
 * a synthetic three-tier ladder, so they would not notice if the real datapack went missing or a
 * default flipped.
 *
 * <p>These tests assert the <em>default</em> configuration, so a {@code run/config} directory left
 * over from an older build can turn them red without any code having changed. If they fail while
 * the ladder looks right, check that {@code item_xp.enabled} still says {@code true}.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class ProgressionGameTest {

    private ProgressionGameTest() {
    }

    @GameTest
    public static void anItemAtTheRequiredExperienceAdvancesExactlyOneTier(final GameTestHelper helper) {
        final TierDefinition uncommon = GameTestTiers.tier(helper, TierDomain.WEAPON, "uncommon");
        final ItemStack stack = GameTestTiers.tieredSword(helper, "common");
        UpgradeService.getInstance().setStoredExperience(stack, TierDomain.WEAPON, uncommon.experienceRequired());

        final ItemStack upgraded = UpgradeService.getInstance().tryAutoUpgrade(stack, helper.getLevel().getServer())
                .orElseThrow(() -> new GameTestAssertException(
                        "an item holding " + uncommon.experienceRequired() + " xp should have advanced"));

        helper.assertTrue(UpgradeService.getInstance().getCurrentTier(upgraded, TierDomain.WEAPON)
                        .filter(tier -> tier.id().equals(uncommon.id())).isPresent(),
                "expected to land on " + uncommon.id());
        helper.assertTrue(upgraded.getItem() == Items.IRON_SWORD, "tier changes, material does not");
        helper.assertTrue(UpgradeService.getInstance().getStoredExperience(upgraded, TierDomain.WEAPON) == 0,
                "an item that spent exactly the requirement should keep nothing");
        helper.assertTrue(UpgradeService.getInstance().getCurrentTier(stack, TierDomain.WEAPON)
                        .filter(tier -> tier.id().equals(GameTestTiers.tierId("common"))).isPresent(),
                "the original stack must be left alone");
        helper.succeed();
    }

    /**
     * Attunement is what makes a branch choice explicit; leaving the selection on the item after
     * spending it would silently pin every later upgrade to that same branch.
     */
    @GameTest
    public static void attuningSelectsABranchAndTheUpgradeSpendsIt(final GameTestHelper helper) {
        final TierDefinition uncommon = GameTestTiers.tier(helper, TierDomain.WEAPON, "uncommon");
        final ItemStack stack = GameTestTiers.tieredSword(helper, "common");

        final boolean attuned = UpgradeService.getInstance().attuneStack(stack, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(attuned, "the shipped common -> uncommon addition is an iron ingot");
        helper.assertTrue(UpgradeService.getInstance().getSelectedBranchId(stack, TierDomain.WEAPON).isPresent(),
                "attunement should have recorded which branch was chosen");

        UpgradeService.getInstance().setStoredExperience(stack, TierDomain.WEAPON, uncommon.experienceRequired());
        final ItemStack upgraded = UpgradeService.getInstance().tryAutoUpgrade(stack, helper.getLevel().getServer())
                .orElseThrow(() -> new GameTestAssertException("the attuned branch should still be upgradeable"));

        helper.assertFalse(UpgradeService.getInstance().getSelectedBranchId(upgraded, TierDomain.WEAPON).isPresent(),
                "the branch selection must be spent by the upgrade it was made for");
        helper.succeed();
    }

    /** At the top of the ladder there is nothing to spend experience on, and that must be quiet. */
    @GameTest
    public static void anItemAtTheTopOfTheLadderSimplyStops(final GameTestHelper helper) {
        final ItemStack stack = GameTestTiers.tieredSword(helper, "epic");
        UpgradeService.getInstance().setStoredExperience(stack, TierDomain.WEAPON, 10_000);

        final Optional<ItemStack> upgraded = UpgradeService.getInstance().tryAutoUpgrade(stack, helper.getLevel().getServer());

        helper.assertFalse(upgraded.isPresent(), "an epic item has nowhere further to go");
        helper.assertTrue(UpgradeService.getInstance().getStoredExperience(stack, TierDomain.WEAPON) == 10_000,
                "unspendable experience should stay on the item rather than be discarded");
        helper.succeed();
    }
}
