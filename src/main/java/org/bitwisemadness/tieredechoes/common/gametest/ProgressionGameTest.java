package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.gamerule.TEGameRules;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.util.List;

/**
 * The ladder and the switches that govern it, as they actually shipped — the unit tests drive a
 * synthetic three-tier ladder, so they would not notice if the real datapack went missing or a
 * default flipped.
 *
 * <p>These tests assert the <em>default</em> configuration, so a {@code run/config} directory left
 * over from an older build can turn them red without any code having changed.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class ProgressionGameTest {

    private ProgressionGameTest() {
    }

    /**
     * The shape of the ladder a player actually meets, read off the loaded datapack.
     *
     * <p>Asserted here rather than only in the unit tests because the shipped ladder is assembled
     * from JSON at reload and the unit tests never see it. A rung that has quietly lost its
     * material, or a top rung that still has somewhere to go, is a broken ladder that compiles.</p>
     */
    @GameTest
    public static void theShippedWeaponLadderHasFiveRungsWithOneRouteOutOfEach(final GameTestHelper helper) {
        final List<TierDefinition> rungs = TierService.getInstance().getOrderedTiers(TierDomain.WEAPON);
        helper.assertTrue(rungs.size() == 5,
                "the shipped ladder should have five rungs, found " + rungs.size());

        // The first rung is joined from outside the ladder, so it is the only one with nothing
        // arriving; every rung after it has exactly one way up and one way on.
        helper.assertTrue(rungs.get(0).upgradeIngredient() != null,
                rungs.get(0).id() + " should declare what joining the ladder costs");
        helper.assertTrue(PathService.getInstance().getIncomingEdges(rungs.get(0).id(), TierDomain.WEAPON).isEmpty(),
                rungs.get(0).id() + " is the entry rung and should have nothing arriving from below");

        for (int index = 1; index < rungs.size(); index++) {
            final TierDefinition rung = rungs.get(index);
            helper.assertTrue(rung.upgradeIngredient() != null,
                    rung.id() + " should declare what joining it costs");
            helper.assertTrue(PathService.getInstance().getIncomingEdges(rung.id(), TierDomain.WEAPON).size() == 1,
                    rung.id() + " should have exactly one way up");

            final int outgoing = PathService.getInstance().getOutgoingEdges(rung.id(), TierDomain.WEAPON).size();
            final boolean isTop = index == rungs.size() - 1;
            if (isTop) {
                helper.assertTrue(outgoing == 0,
                        rung.id() + " is the top rung and should have nowhere further to go, found " + outgoing);
            } else {
                helper.assertTrue(outgoing == 1,
                        rung.id() + " should lead on to the next rung, found " + outgoing + " routes");
            }
        }
        helper.succeed();
    }

    /**
     * Turning the tier system off makes an item's rung unreadable, and turning it back on recovers it.
     *
     * <p>This is the whole contract behind {@code tier_system}: a switch that governs a system's
     * <em>existence</em> rather than its progress. A switch that erased the state instead would pass
     * the first half of this test and fail the second, which is the half a player would notice when
     * they turned tiers back on and found every item stripped.</p>
     *
     * <p>Driven through {@code /gamerule} rather than by poking the services directly, because the
     * command is the route an operator actually takes and the wiring it exercises — the change
     * callback, the re-resolve, the re-read — is the part most likely to break.</p>
     */
    @GameTest
    public static void forcingTheTierSystemOffIgnoresARungWithoutErasingIt(final GameTestHelper helper) {
        final ResourceLocation rareId = GameTestTiers.tierId("rare");
        final ItemStack stack = GameTestTiers.tieredSword(helper, "rare");
        helper.assertTrue(UpgradeService.getInstance().getCurrentTier(stack, TierDomain.WEAPON)
                        .filter(tier -> tier.id().equals(rareId)).isPresent(),
                "the ladder should read as tiered while the switch is at its default");

        final String storedBefore = storedRungId(stack);
        try {
            gamerule(helper, TEGameRules.TIERS.toString(), TEGameRules.FORCE_OFF);

            helper.assertTrue(UpgradeService.getInstance().getCurrentTier(stack, TierDomain.WEAPON).isEmpty(),
                    "forcing the tier system off must make the stored rung unreadable");
            helper.assertTrue(UpgradeService.getInstance().getAllCurrentTiers(stack).isEmpty(),
                    "no ladder may be read while the tier system is off");
            helper.assertTrue(storedBefore.equals(storedRungId(stack)),
                    "switching the system off must leave the stored rung on the item, expected "
                            + storedBefore + " but found " + storedRungId(stack));
        } finally {
            // Restored no matter how the assertions above went: a rule left forced off would turn
            // every later test in this batch red and point at the wrong cause.
            gamerule(helper, TEGameRules.TIERS.toString(), TEGameRules.FOLLOW_DATAPACK);
        }

        helper.assertTrue(UpgradeService.getInstance().getCurrentTier(stack, TierDomain.WEAPON)
                        .filter(tier -> tier.id().equals(rareId)).isPresent(),
                "handing the decision back to the datapack must recover the rung");
        helper.succeed();
    }

    /** Runs a command the way an operator would, without the output reaching the log. */
    private static void gamerule(final GameTestHelper helper, final String rule, final int value) {
        final MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "gamerule " + rule + " " + value);
    }

    /** The rung id the item carries on the weapon ladder, read straight off the NBT. */
    private static String storedRungId(final ItemStack stack) {
        return stack.getTag().getCompound(TierService.ROOT_TAG)
                .getCompound(TierService.DOMAINS_TAG)
                .getCompound(TierDomain.WEAPON.getSerializedName())
                .getString(TierService.TIER_ID_TAG);
    }
}
