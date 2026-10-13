package org.bitwisemadness.tieredechoes.common.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The level's state machine, driven against real stacks: banking, stepping, capping, and the two
 * switches that decide whether any of it is visible.
 *
 * <p>The service writes through to a stack's own NBT rather than returning a copy, because unlike
 * a smithing transition there is no recipe to apply — the item in the player's hand is the thing
 * progressing, and the writes are the progression. That makes each test its own fixture: a fresh
 * sword, one policy, and the shipped curves unless a test says otherwise.</p>
 */
class LevelServiceTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @BeforeEach
    void setUp() {
        PolicyService.getInstance().applyResolved(TierPolicy.defaults());
        LevelService.getInstance().replaceAll(LevelConfig.defaults());
        TierService.getInstance().replaceAll(List.of(weaponTier(1.6D, 1.55D)));
    }

    @AfterEach
    void tearDown() {
        PolicyService.getInstance().applyResolved(TierPolicy.defaults());
        LevelService.getInstance().replaceAll(LevelConfig.defaults());
        TierService.getInstance().replaceAll(List.of());
    }

    @Test
    void awardsAccumulateUntilTheyCoverTheNextLevel() {
        final ItemStack sword = tieredSword();

        assertTrue(LevelService.getInstance().addExperience(sword, 100).isEmpty(),
                "100 of the 200 level 2 costs must not step");
        assertEquals(1, LevelService.getInstance().getLevel(sword));
        assertEquals(100, LevelService.getInstance().getStoredExperience(sword));

        assertTrue(LevelService.getInstance().addExperience(sword, 100).isPresent(),
                "the next 100 completes the step");
        assertEquals(2, LevelService.getInstance().getLevel(sword));
        assertEquals(0, LevelService.getInstance().getStoredExperience(sword),
                "a step spends exactly its cost");
    }

    @Test
    void aLevelUpCarriesTheRemainderInsteadOfBurningIt() {
        final ItemStack sword = tieredSword();

        LevelService.getInstance().addExperience(sword, 250);

        assertEquals(2, LevelService.getInstance().getLevel(sword));
        assertEquals(50, LevelService.getInstance().getStoredExperience(sword),
                "250 buys level 2's 200 and leaves the other 50 on its way to level 3");
    }

    @Test
    void oneAwardCanCrossSeveralLevels() {
        final ItemStack sword = tieredSword();

        // 900 covers level 2 (200) and level 3 (675) and leaves 25 — a kill on a low-level item
        // is worth more than one step, and the loop has to say so rather than stopping at one.
        final var leveled = LevelService.getInstance().addExperience(sword, 900);

        assertTrue(leveled.isPresent() && leveled.getAsInt() == 3,
                "one award should be able to take two steps at once");
        assertEquals(25, LevelService.getInstance().getStoredExperience(sword));
    }

    @Test
    void anItemAtTheCapNeitherGainsNorStores() {
        final ItemStack sword = tieredSword();
        // 75 600 is every step's cost summed: 200+675+1600+3125+5400+8575+12800+18225+25000.
        LevelService.getInstance().addExperience(sword, 75_600);

        assertEquals(10, LevelService.getInstance().getLevel(sword), "the cap is ten");
        assertEquals(0, LevelService.getInstance().getStoredExperience(sword),
                "there is no next level to be short of, so nothing may be stored");

        assertTrue(LevelService.getInstance().addExperience(sword, 1_000).isEmpty(),
                "an item at the cap cannot be fed further");
        assertEquals(10, LevelService.getInstance().getLevel(sword));
    }

    @Test
    void onlyAnItemHoldingARungCanEarn() {
        final ItemStack plain = new ItemStack(Items.IRON_SWORD);

        assertFalse(LevelService.getInstance().canEarn(plain),
                "an untiered item would gain a level that changes nothing");
        assertTrue(LevelService.getInstance().addExperience(plain, 500).isEmpty());
        assertEquals(1, LevelService.getInstance().getLevel(plain));
        assertFalse(plain.hasTag() && plain.getTag().contains(TierService.ROOT_TAG)
                        && plain.getTag().getCompound(TierService.ROOT_TAG).contains(LevelService.LEVEL_TAG),
                "nothing at all may be written to an item that cannot earn");
    }

    @Test
    void turningLevelUpOffFreezesThePoolWithoutLosingIt() {
        final ItemStack sword = tieredSword();
        LevelService.getInstance().addExperience(sword, 100);

        PolicyService.getInstance().applyResolved(new TierPolicy(true, true, true, false, false));

        assertTrue(LevelService.getInstance().addExperience(sword, 100).isEmpty(),
                "a frozen route must not accept new experience");
        assertEquals(1, LevelService.getInstance().getLevel(sword));
        assertEquals(100, LevelService.getInstance().getStoredExperience(sword),
                "the switch closes the route; what was earned stays");

        PolicyService.getInstance().applyResolved(TierPolicy.defaults());

        assertTrue(LevelService.getInstance().addExperience(sword, 100).isPresent(),
                "handing the decision back must let the pool finish its step");
        assertEquals(2, LevelService.getInstance().getLevel(sword));
    }

    /** The whole hide-don't-erase contract, in the level system's own half of it. */
    @Test
    void turningTheLevelSystemOffHidesTheLevelWithoutErasingIt() {
        final ItemStack sword = tieredSword();
        LevelService.getInstance().addExperience(sword, 250);

        PolicyService.getInstance().applyResolved(new TierPolicy(true, true, false, true, false));

        assertEquals(1, LevelService.getInstance().getLevel(sword),
                "with the system off every item reads as level one");
        assertEquals(0, LevelService.getInstance().getStoredExperience(sword));
        assertEquals(1.0D, LevelService.getInstance().levelFactor(sword), 1.0E-12D,
                "a hidden level must not scale anything — and must never scale it down");
        assertEquals(2, storedLevel(sword), "the level itself must survive the switch being thrown");

        PolicyService.getInstance().applyResolved(TierPolicy.defaults());

        assertEquals(2, LevelService.getInstance().getLevel(sword),
                "switching the system back on recovers every level that was there");
        assertEquals(50, LevelService.getInstance().getStoredExperience(sword));
    }

    @Test
    void theLevelFactorScalesTheTierMultiplierFromBothSides() {
        final ItemStack sword = tieredSword();
        assertEquals(1.6D, TierService.getInstance().getAttributeMultiplier(sword, Attributes.ATTACK_DAMAGE).orElseThrow(),
                1.0E-12D, "level 1 presents the tier exactly as declared");

        LevelService.getInstance().addExperience(sword, 200);

        // effective = 1 + (tier − 1) × factor, with factor 1.02 at level 2.
        assertEquals(1.0D + 0.6D * 1.02D,
                TierService.getInstance().getAttributeMultiplier(sword, Attributes.ATTACK_DAMAGE).orElseThrow(),
                1.0E-12D, "attributes take the level's share");
        assertEquals(1.0D + 0.55D * 1.02D,
                TierService.getInstance().getScalarMultiplier(sword, "durability").orElseThrow(),
                1.0E-12D, "and so does durability, through the same two seams");
    }

    @Test
    void anItemWithNoRungOnItsStackChangesNothing() {
        final ItemStack bare = new ItemStack(Items.IRON_SWORD);

        assertTrue(TierService.getInstance().getAttributeMultiplier(bare, Attributes.ATTACK_DAMAGE).isEmpty(),
                "an untiered item has no multiplier to scale in the first place");
        assertEquals(1.0D, LevelService.getInstance().levelFactor(bare), 1.0E-12D);
    }

    /** A level the datapack removed from the ladder reads as the cap rather than as a ghost. */
    @Test
    void aStoredLevelBeyondTheCurrentCapReadsAsTheCap() {
        final ItemStack sword = tieredSword();
        writeLevel(sword, 99, 70);

        assertEquals(10, LevelService.getInstance().getLevel(sword));
        assertTrue(LevelService.getInstance().addExperience(sword, 100).isEmpty(),
                "an item the cap has passed can neither step nor store");
    }

    // ---------------------------------------------------------------- fixture

    private static ItemStack tieredSword() {
        return UpgradeService.getInstance().applyTier(new ItemStack(Items.IRON_SWORD),
                TierService.getInstance().getTier(TestTiers.id("alpha"), TierDomain.WEAPON).orElseThrow());
    }

    private static TierDefinition weaponTier(final double attackDamage, final double durability) {
        return new TierDefinition(TestTiers.id("alpha"), TierDomain.WEAPON, 1, Rarity.COMMON, 0,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, new net.minecraft.resources.ResourceLocation("minecraft", "stick")),
                Optional.empty(), Optional.empty(), Optional.of(TestTiers.NAMESPACE),
                Map.of("attack_damage", attackDamage, "durability", durability),
                Map.of(), Map.of(), true, true);
    }

    /** The level straight off the NBT, bypassing every gate — what "not erased" means. */
    private static int storedLevel(final ItemStack stack) {
        return levelTag(stack).getInt(LevelService.LEVEL_VALUE_TAG);
    }

    private static void writeLevel(final ItemStack stack, final int level, final int experience) {
        final CompoundTag tag = stack.getOrCreateTagElement(TierService.ROOT_TAG);
        final CompoundTag levelTag = new CompoundTag();
        levelTag.putInt(LevelService.LEVEL_VALUE_TAG, level);
        levelTag.putInt(LevelService.STORED_EXPERIENCE_TAG, experience);
        tag.put(LevelService.LEVEL_TAG, levelTag);
    }

    private static CompoundTag levelTag(final ItemStack stack) {
        return stack.getTag().getCompound(TierService.ROOT_TAG).getCompound(LevelService.LEVEL_TAG);
    }
}
