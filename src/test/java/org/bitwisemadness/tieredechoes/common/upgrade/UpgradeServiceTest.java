package org.bitwisemadness.tieredechoes.common.upgrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.common.data.loader.ShippedDatapack;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestConfig;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progression contract: what an item keeps, what it spends, and when it advances.
 *
 * <p>A synthetic three-tier ladder is installed rather than the shipped one so each test can pin an
 * exact experience number instead of reasoning about four tiers across four domains.</p>
 */
class UpgradeServiceTest {

    private static final int BETA_REQUIRED_XP = 100;
    private static final int GAMMA_REQUIRED_XP = 250;

    private UpgradeService service;

    @BeforeEach
    void setUp() {
        TestBootstrap.init();
        TestConfig.load();
        this.service = UpgradeService.getInstance();
        PolicyService.getInstance().replace(TierPolicy.defaults());
        TierService.getInstance().replaceAll(List.of(
                TestTiers.tier("alpha", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("beta", TierDomain.WEAPON, 1, BETA_REQUIRED_XP),
                TestTiers.tier("gamma", TierDomain.WEAPON, 2, GAMMA_REQUIRED_XP)));
        PathService.getInstance().replaceAll(List.of(
                TestTiers.edge("alpha", "beta", TierDomain.WEAPON, 1,
                        Optional.of(TestTiers.item("iron_ingot")), Optional.of(TestTiers.id("alpha")),
                        Optional.of("branch_alpha_beta")),
                TestTiers.edge("beta", "gamma", TierDomain.WEAPON, 2,
                        Optional.of(TestTiers.item("diamond")), Optional.of(TestTiers.id("beta")),
                        Optional.of("branch_beta_gamma"))));
    }

    @AfterEach
    void tearDown() {
        ShippedDatapack.restoreShippedState();
        PolicyService.getInstance().replace(TierPolicy.defaults());
        TestConfig.resetToDefaults();
    }

    private TierDefinition tier(final String path) {
        return TierService.getInstance().getTier(TestTiers.id(path), TierDomain.WEAPON).orElseThrow();
    }

    private ItemStack atTier(final String path) {
        return this.service.applyTier(new ItemStack(Items.IRON_SWORD), tier(path));
    }

    // ---------------------------------------------------------------- experience

    @Test
    void experienceAccumulatesOnTheStack() {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);

        this.service.addExperience(stack, 40);
        this.service.addExperience(stack, 60);

        assertEquals(100, this.service.getStoredExperience(stack));
    }

    @Test
    void experienceIgnoresEmptyStacksAndNonPositiveAmounts() {
        final ItemStack empty = ItemStack.EMPTY;
        this.service.addExperience(empty, 50);
        assertEquals(0, this.service.getStoredExperience(empty));

        final ItemStack stack = new ItemStack(Items.IRON_SWORD);
        this.service.addExperience(stack, 0);
        this.service.addExperience(stack, -10);
        assertEquals(0, this.service.getStoredExperience(stack));
    }

    @Test
    void aNegativeStoredAmountIsClampedRatherThanRewindingProgression() {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);

        this.service.setStoredExperience(stack, -25);

        assertEquals(0, this.service.getStoredExperience(stack));
    }

    /** The read path must not need the TieredEchoes tag to exist at all. */
    @Test
    void anUntaggedStackReportsZeroExperience() {
        assertEquals(0, this.service.getStoredExperience(new ItemStack(Items.IRON_SWORD)));
    }

    // ---------------------------------------------------------------- the xp gate

    @Test
    void anItemJustBelowTheRequirementDoesNotAdvance() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP - 1);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
        assertEquals(TierDomain.WEAPON, this.service.getCurrentTier(stack).orElseThrow().domain());
        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(stack).orElseThrow().id());
    }

    @Test
    void reachingTheRequirementAdvancesExactlyOneTierAndLeavesTheMaterialAlone() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertEquals(TestTiers.id("beta"), this.service.getCurrentTier(upgraded).orElseThrow().id());
        assertEquals(Items.IRON_SWORD, upgraded.getItem(), "tier changes, material does not");
        assertEquals(0, this.service.getStoredExperience(upgraded), "an exact requirement spends everything");
    }

    @Test
    void excessExperienceCarriesOverByDefault() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP + 35);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertEquals(35, this.service.getStoredExperience(upgraded));
    }

    @Test
    void carryOverCanBeSwitchedOffByThePolicy() {
        PolicyService.getInstance().replace(new TierPolicy(TierPolicy.Mode.HYBRID, TierPolicy.MultiPathResolution.RANDOM,
                false, false, null));
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP + 35);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertEquals(0, this.service.getStoredExperience(upgraded),
                "carry_over_excess_xp=false is the pacing lever for datapacks that find xp too fast");
    }

    @Test
    void theSourceStackIsNeverMutatedInPlace() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertNotSame(stack, upgraded);
        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(stack).orElseThrow().id(),
                "callers rely on getting a new stack back, with the original left for the inventory");
    }

    // ---------------------------------------------------------------- material upgrades

    /** A diamond-to-netherite smithing upgrade changes the item, not its tier bookkeeping. */
    @Test
    void applyingANewTierPreservesStoredExperience() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, 42);

        final ItemStack retiered = this.service.applyTier(stack, tier("beta"));

        assertEquals(42, this.service.getStoredExperience(retiered));
        assertEquals(TestTiers.id("beta"), this.service.getCurrentTier(retiered).orElseThrow().id());
    }

    @Test
    void applyingANewTierIsAPreviewFreeWriteSoTheNameStaysRenderedAtRenderTime() {
        final ItemStack stack = atTier("alpha");
        final CompoundTag te = stack.getTag().getCompound("TieredEchoes");

        assertFalse(te.getBoolean("preview"), "a real tier write must not be flagged as a preview");
        assertEquals("weapon", te.getString("tier_domain"));
        assertEquals(TestTiers.id("alpha").toString(), te.getString("tier_id"));
    }

    @Test
    void previewWritesAreMarkedSoTheTooltipCanDistinguishThem() {
        final ItemStack preview = this.service.applyPreviewTier(new ItemStack(Items.IRON_SWORD), tier("gamma"));

        assertTrue(preview.getTag().getCompound("TieredEchoes").getBoolean("preview"));
        assertEquals(TestTiers.id("gamma"), this.service.getCurrentTier(preview).orElseThrow().id());
        assertEquals(0, this.service.getStoredExperience(preview), "a preview must not claim the item has earned anything");
    }

    // ---------------------------------------------------------------- downgrade

    @Test
    void downgradingStepsBackOneTierAndKeepsExperience() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, 42);
        final ItemStack atBeta = this.service.applyTier(stack, tier("beta"));

        final ItemStack downgraded = this.service.downgrade(atBeta);

        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(downgraded).orElseThrow().id());
        assertEquals(42, this.service.getStoredExperience(downgraded), "a downgrade is not a punishment");
    }

    @Test
    void downgradingAnItemWithNoDomainIsACopyAndLeavesItAlone() {
        final ItemStack stack = new ItemStack(Items.POTATO);

        final ItemStack result = this.service.downgrade(stack);

        assertEquals(Items.POTATO, result.getItem());
        assertTrue(this.service.getCurrentTier(result).isEmpty());
    }

    @Test
    void downgradingFromTheBottomOfTheLadderDoesNothing() {
        final ItemStack stack = atTier("alpha");

        assertEquals(TestTiers.id("alpha"),
                this.service.getCurrentTier(this.service.downgrade(stack)).orElseThrow().id());
    }

    // ---------------------------------------------------------------- branches and attunement

    @Test
    void attuningSelectsTheBranchWhoseIngredientMatchesTheOffhand() {
        final ItemStack main = atTier("alpha");

        final boolean attuned = this.service.attuneStack(main, new ItemStack(Items.IRON_INGOT));

        assertTrue(attuned);
        assertEquals(Optional.of("branch_alpha_beta"), this.service.getSelectedBranchId(main));
    }

    @Test
    void anOffhandThatMatchesNoBranchDoesNotAtune() {
        final ItemStack main = atTier("alpha");

        assertFalse(this.service.attuneStack(main, new ItemStack(Items.STICK)));
        assertTrue(this.service.getSelectedBranchId(main).isEmpty());
    }

    /**
     * A potato infers no domain at all, so there is no ladder to attune onto — unlike a fresh
     * weapon, which correctly starts at the bottom tier of its domain.
     */
    @Test
    void attuningAnItemThatCannotCarryAnyDomainIsRefused() {
        assertFalse(this.service.attuneStack(new ItemStack(Items.POTATO), new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void attunementIsConsumedByTheUpgradeItWasMadeFor() {
        final ItemStack stack = atTier("alpha");
        this.service.attuneStack(stack, new ItemStack(Items.IRON_INGOT));
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertTrue(this.service.getSelectedBranchId(upgraded).isEmpty(),
                "leaving the branch selection on the item would pin its next upgrade too");
        assertEquals(TestTiers.id("beta"), this.service.getCurrentTier(upgraded).orElseThrow().id());
    }

    @Test
    void attunementIsRecordedForDiagnosticsAndStaysOnTheItemUntilItIsUsed() {
        final ItemStack stack = atTier("alpha");
        this.service.attuneStack(stack, new ItemStack(Items.IRON_INGOT));
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        final ItemStack upgraded = this.service.tryAutoUpgrade(stack).orElseThrow();

        assertEquals(Optional.of("branch_alpha_beta"),
                Optional.of(upgraded.getTag().getCompound("TieredEchoes").getString("last_branch_id")));
    }

    /** A selection left over from a removed or differently-ordered path must not select anything. */
    @Test
    void aStaleBranchSelectionBlocksTheUpgradeInsteadOfPickingArbitrarily() {
        final ItemStack stack = atTier("alpha");
        stack.getOrCreateTagElement("TieredEchoes").putString("selected_branch_id", "branch_beta_gamma");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
        assertEquals(Optional.of("branch_beta_gamma"), this.service.getSelectedBranchId(stack),
                "an unresolvable selection is left alone so the player can see what is wrong");
    }

    @Test
    void clearingASelectionLeavesTheRestOfTheTierStateIntact() {
        final ItemStack stack = atTier("alpha");
        this.service.attuneStack(stack, new ItemStack(Items.IRON_INGOT));
        this.service.setStoredExperience(stack, 17);

        this.service.clearSelectedBranch(stack);

        assertTrue(this.service.getSelectedBranchId(stack).isEmpty());
        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(stack).orElseThrow().id());
        assertEquals(17, this.service.getStoredExperience(stack));
    }

    // ---------------------------------------------------------------- gates

    @Test
    void nothingAdvancesWhileTheXpSystemIsSwitchedOff() {
        TieredEchoesConfig.ENABLE_ITEM_XP.set(false);
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP * 10);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(stack).orElseThrow().id());
    }

    @Test
    void nothingAdvancesWhileXpIsSwitchedOffEvenForTheSinglePathCase() {
        TieredEchoesConfig.ENABLE_ITEM_XP.set(false);
        final ItemStack stack = atTier("beta");
        this.service.setStoredExperience(stack, GAMMA_REQUIRED_XP);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
    }

    @Test
    void smithingOnlyPolicyStopsXpFromDrivingProgression() {
        PolicyService.getInstance().replace(new TierPolicy(TierPolicy.Mode.SMITHING_ONLY,
                TierPolicy.MultiPathResolution.FIRST, true, false, null));
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP * 10);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
    }

    @Test
    void autoOnlyPolicyStillDrivesProgression() {
        PolicyService.getInstance().replace(new TierPolicy(TierPolicy.Mode.AUTO_ONLY,
                TierPolicy.MultiPathResolution.FIRST, true, false, null));
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, BETA_REQUIRED_XP);

        assertEquals(TestTiers.id("beta"),
                this.service.getCurrentTier(this.service.tryAutoUpgrade(stack).orElseThrow()).orElseThrow().id());
    }

    @Test
    void reachingTheTopOfTheLadderStopsAdvancingRatherThanErroring() {
        final ItemStack stack = atTier("gamma");
        this.service.setStoredExperience(stack, 10_000);

        assertEquals(Optional.empty(), this.service.tryAutoUpgrade(stack));
        assertEquals(TestTiers.id("gamma"), this.service.getCurrentTier(stack).orElseThrow().id());
    }

    // ---------------------------------------------------------------- description

    @Test
    void describeStackNamesTheItemEvenWhenItHasNoTier() {
        final String described = this.service.describeStack(new ItemStack(Items.IRON_SWORD)).getString();

        assertTrue(described.contains("Iron Sword"), described);
    }

    @Test
    void describeStackSummarisesTierExperienceAndBranch() {
        final ItemStack stack = atTier("alpha");
        this.service.setStoredExperience(stack, 12);

        final String described = this.service.describeStack(stack).getString();

        assertTrue(described.contains("tier=" + TestTiers.id("alpha")), described);
        assertTrue(described.contains("xp=12"), described);
        assertTrue(described.contains("branch=none"), described);
    }
}
