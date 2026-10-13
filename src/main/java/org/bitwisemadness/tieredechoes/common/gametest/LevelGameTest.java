package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.gamerule.TEGameRules;
import org.bitwisemadness.tieredechoes.common.level.LevelService;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;

import java.util.OptionalInt;

/**
 * The Level system as a player meets it: the shipped curves, the real datapack, and the two
 * gamerules that govern whether any of it moves.
 *
 * <p>Same reasoning as {@link ProgressionGameTest} — the unit tests drive a synthetic ladder and a
 * hand-set policy, so none of them would notice if the shipped curves went missing or a default
 * flipped. These assert the defaults a fresh world boots with.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class LevelGameTest {

    private LevelGameTest() {
    }

    /**
     * The whole loop, end to end: experience banks, the level steps, and the step's share of the
     * tier's multiplier reaches the item's attributes through the same seams everything else reads.
     *
     * <p>250 experience is deliberately not a whole number of levels: level 2 costs 200 under the
     * shipped curve, so the test also pins the carry-over a player would see in the tooltip.</p>
     */
    @GameTest
    public static void usingATieredItemRaisesItsLevelAndScalesItsAttributes(final GameTestHelper helper) {
        final ItemStack sword = GameTestTiers.tieredSword(helper, "epic");
        final TierDefinition epic = GameTestTiers.tier(helper, TierDomain.WEAPON, "epic");

        final OptionalInt leveled = LevelService.getInstance().addExperience(sword, 250);

        helper.assertTrue(leveled.isPresent() && leveled.getAsInt() == 2,
                "250 experience should reach level 2 (the step costs 200), reached " + leveled);
        helper.assertTrue(LevelService.getInstance().getStoredExperience(sword) == 50,
                "the 50 the level-2 step did not need should carry toward level 3");

        final double tierMultiplier = epic.attributeMultipliers().get("attack_damage");
        final double expected = 1.0D + (tierMultiplier - 1.0D) * 1.02D;
        final double actual = TierService.getInstance()
                .getAttributeMultiplier(sword, Attributes.ATTACK_DAMAGE).orElseThrow();
        helper.assertTrue(Math.abs(expected - actual) < 1.0E-9D,
                "effective multiplier should be 1 + (tier - 1) x 1.02 = " + expected + ", found " + actual);
        helper.assertTrue(Math.abs(actual - epic.attributeMultipliers().get("attack_damage")) > 1.0E-9D,
                "the level should visibly move the tier's own multiplier");
        helper.succeed();
    }

    /**
     * The progression switch closes the route without rewinding it — the same contract the tier
     * side runs, driven through {@code /gamerule} because that is the route an operator takes.
     */
    @GameTest
    public static void forcingLevelUpOffFreezesAnItemWithoutLosingWhatItEarned(final GameTestHelper helper) {
        final ItemStack sword = GameTestTiers.tieredSword(helper, "rare");
        LevelService.getInstance().addExperience(sword, 100);

        try {
            gamerule(helper, TEGameRules.LEVEL_UP.toString(), TEGameRules.FORCE_OFF);

            helper.assertTrue(LevelService.getInstance().addExperience(sword, 500).isEmpty(),
                    "a frozen route must not accept new experience");
            helper.assertTrue(LevelService.getInstance().getLevel(sword) == 1,
                    "and it must not move the level either");
            helper.assertTrue(LevelService.getInstance().getStoredExperience(sword) == 100,
                    "what was earned stays where it was");
        } finally {
            gamerule(helper, TEGameRules.LEVEL_UP.toString(), TEGameRules.FOLLOW_DATAPACK);
        }

        helper.assertTrue(LevelService.getInstance().addExperience(sword, 100).isPresent(),
                "handing the decision back to the datapack must let the pool finish its step");
        helper.assertTrue(LevelService.getInstance().getLevel(sword) == 2,
                "the 100 already banked plus 100 more is exactly level 2's cost");
        helper.succeed();
    }

    /** The hide-don't-erase contract in the level system's own half. */
    @GameTest
    public static void forcingTheLevelSystemOffHidesALevelWithoutErasingIt(final GameTestHelper helper) {
        final ItemStack sword = GameTestTiers.tieredSword(helper, "rare");
        LevelService.getInstance().addExperience(sword, 250);
        final String storedBefore = storedLevel(sword);
        helper.assertTrue(LevelService.getInstance().getLevel(sword) == 2,
                "the sword should read as level 2 while the switch is at its default");

        try {
            gamerule(helper, TEGameRules.LEVELS.toString(), TEGameRules.FORCE_OFF);

            helper.assertTrue(LevelService.getInstance().getLevel(sword) == 1,
                    "with the system off, every item reads as level one");
            helper.assertTrue(LevelService.getInstance().getStoredExperience(sword) == 0,
                    "and the pool reads as empty");
            helper.assertTrue(LevelService.getInstance().levelFactor(sword) == 1.0D,
                    "a hidden level must never scale an item down — factor 1.0 is the tier exactly as declared");
            helper.assertTrue(storedBefore.equals(storedLevel(sword)),
                    "the level itself must survive the switch, expected " + storedBefore + " but found " + storedLevel(sword));
        } finally {
            gamerule(helper, TEGameRules.LEVELS.toString(), TEGameRules.FOLLOW_DATAPACK);
        }

        helper.assertTrue(LevelService.getInstance().getLevel(sword) == 2,
                "handing the decision back must recover the level");
        helper.succeed();
    }

    /**
     * Only items that hold a rung can earn. Levels amplify a tier's multiplier, so an untiered
     * item would gain a level that changes nothing — the fresh sword in a player's first hour must
     * not advertise a journey it has not been invited on.
     */
    @GameTest
    public static void anUntieredItemNeverEarnsALevel(final GameTestHelper helper) {
        final ItemStack plain = new ItemStack(Items.IRON_SWORD);

        helper.assertFalse(LevelService.getInstance().canEarn(plain),
                "an item holding no rung cannot earn");
        helper.assertTrue(LevelService.getInstance().addExperience(plain, 500).isEmpty(),
                "and an add attempt must leave it with nothing to show");
        helper.assertTrue(LevelService.getInstance().getLevel(plain) == 1,
                "and it stays at the level every item starts at");
        helper.succeed();
    }

    /** Runs a command the way an operator would, without the output reaching the log. */
    private static void gamerule(final GameTestHelper helper, final String rule, final int value) {
        final MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "gamerule " + rule + " " + value);
    }

    /** The level the item carries, read straight off the NBT — past every gate. */
    private static String storedLevel(final ItemStack stack) {
        return String.valueOf(stack.getTag().getCompound(TierService.ROOT_TAG)
                .getCompound(LevelService.LEVEL_TAG).getInt(LevelService.LEVEL_VALUE_TAG));
    }
}
