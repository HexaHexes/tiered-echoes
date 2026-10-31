package org.bitwisemadness.tieredechoes.common.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.common.data.loader.ShippedDatapack;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestTags;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What an item carries between runs: which rung it sits on per ladder, and what the switches say
 * about whether any of that is readable.
 *
 * <p>A synthetic three-tier ladder is installed rather than the shipped one so each test can pin an
 * exact rung instead of reasoning about four tiers across every domain.</p>
 */
class UpgradeServiceTest {

    private UpgradeService service;

    @BeforeEach
    void setUp() {
        TestBootstrap.init();
        // Domain inference is tag-driven and a test JVM ships no datapack, so tags start unbound.
        // Clearing here keeps one test's binding from deciding another test's domains.
        TestTags.clear();
        this.service = UpgradeService.getInstance();
        PolicyService.getInstance().applyResolved(TierPolicy.defaults());
        TierService.getInstance().replaceAll(List.of(
                TestTiers.tier("alpha", TierDomain.WEAPON, 0),
                TestTiers.tier("beta", TierDomain.WEAPON, 1),
                TestTiers.tier("gamma", TierDomain.WEAPON, 2)));
        PathService.getInstance().replaceAll(List.of(
                TestTiers.edge("alpha", "beta", TierDomain.WEAPON, 1,
                        Optional.of(TestTiers.item("iron_ingot")), Optional.empty(), Optional.of("branch_alpha_beta")),
                TestTiers.edge("beta", "gamma", TierDomain.WEAPON, 2,
                        Optional.of(TestTiers.item("diamond")), Optional.empty(), Optional.of("branch_beta_gamma"))));
    }

    @AfterEach
    void tearDown() {
        ShippedDatapack.restoreShippedState();
        PolicyService.getInstance().applyResolved(TierPolicy.defaults());
        TestTags.clear();
    }

    private TierDefinition tier(final String path) {
        return TierService.getInstance().getTier(TestTiers.id(path), TierDomain.WEAPON).orElseThrow();
    }

    private ItemStack atTier(final String path) {
        return this.service.applyTier(new ItemStack(Items.IRON_SWORD), tier(path));
    }

    // ---------------------------------------------------------------- writing a rung

    @Test
    void applyingANewTierIsAPreviewFreeWriteSoTheNameStaysRenderedAtRenderTime() {
        final ItemStack stack = atTier("alpha");
        final CompoundTag weapon = stack.getTag().getCompound("TieredEchoes")
                .getCompound("domains").getCompound("weapon");

        assertFalse(weapon.getBoolean("preview"), "a real tier write must not be flagged as a preview");
        assertEquals(TestTiers.id("alpha").toString(), weapon.getString("tier_id"));
    }

    /**
     * An item belongs to a domain because a tag says so, and an item in two tags belongs to two
     * domains — so the ladder state has to be keyed per domain rather than sitting flat at the root,
     * where a second ladder would overwrite the first.
     */
    @Test
    void ladderStateIsKeyedByDomainSoTwoLaddersCanCoexist() {
        final CompoundTag domains = this.service.applyTier(new ItemStack(Items.IRON_SWORD), tier("alpha"))
                .getTag().getCompound("TieredEchoes").getCompound("domains");

        assertTrue(domains.contains("weapon"), "the weapon ladder should be written under its own key");
        assertFalse(domains.contains("tool"), "an iron sword climbs no tool ladder, so it must write none");
    }

    @Test
    void previewWritesAreMarkedSoTheTooltipCanDistinguishThem() {
        final ItemStack preview = this.service.applyPreviewTier(new ItemStack(Items.IRON_SWORD), tier("gamma"));

        assertTrue(preview.getTag().getCompound("TieredEchoes")
                .getCompound("domains").getCompound("weapon").getBoolean("preview"));
        assertEquals(TestTiers.id("gamma"), this.service.getCurrentTier(preview).orElseThrow().id());
    }

    @Test
    void theSourceStackIsNeverMutatedInPlace() {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);

        final ItemStack upgraded = this.service.applyTier(stack, tier("alpha"));

        assertNotSame(stack, upgraded);
        assertTrue(this.service.getCurrentTier(stack).isEmpty(),
                "callers rely on getting a new stack back, with the original left for the inventory");
        assertEquals(TestTiers.id("alpha"), this.service.getCurrentTier(upgraded).orElseThrow().id());
    }

    // ---------------------------------------------------------------- reading a rung

    @Test
    void anUntaggedStackSitsOnNoLadder() {
        assertTrue(this.service.getCurrentTier(new ItemStack(Items.IRON_SWORD)).isEmpty());
        assertTrue(this.service.getAllCurrentTiers(new ItemStack(Items.IRON_SWORD)).isEmpty());
    }

    @Test
    void thePresentedTierIsTheHighestRungHeldOnAnyLadder() {
        final ItemStack stack = atTier("beta");

        assertEquals(TestTiers.id("beta"), this.service.getCurrentTier(stack).orElseThrow().id(),
                "the name line shows the highest rung, not whichever ladder happened to be read first");
        assertEquals(TestTiers.id("beta"),
                this.service.getCurrentTier(stack, TierDomain.WEAPON).orElseThrow().id(),
                "the scoped read must agree with the unscoped one when only one ladder is involved");
    }

    @Test
    void everyLadderTheItemSitsOnIsReportedTogether() {
        final ItemStack stack = atTier("alpha");

        final Map<TierDomain, TierDefinition> ladders = this.service.getAllCurrentTiers(stack);

        assertEquals(1, ladders.size());
        assertEquals(TestTiers.id("alpha"), ladders.get(TierDomain.WEAPON).id());
    }

    @Test
    void aLadderWithNothingRecordedFallsBackToWhatTheTagsClaim() {
        // Nothing has been written, so the only signal available is which ladders the item's tags
        // bind it to — which is what a fresh item should be treated as wanting.
        TestTags.bindVanillaWeaponDefaults();

        final List<TierDomain> inferred = this.service.ladderDomains(new ItemStack(Items.IRON_SWORD));

        assertEquals(List.of(TierDomain.WEAPON), inferred,
                "an iron sword is bound by the weapon tag, and should offer the weapon ladder");
    }

    @Test
    void anItemOnNoLadderAndInNoTagOffersNothing() {
        TestTags.clear();

        assertEquals(List.of(), this.service.ladderDomains(new ItemStack(Items.IRON_SWORD)));
        assertEquals(List.of(), this.service.ladderDomains(ItemStack.EMPTY));
    }

    @Test
    void anItemWithARungWrittenOffersThatLadderEvenIfItsTagsWereLaterNarrowed() {
        final ItemStack stack = atTier("alpha");
        TestTags.clear();

        assertEquals(List.of(TierDomain.WEAPON), this.service.ladderDomains(stack),
                "the compound says which ladders the item is actually on, and wins over a tag change");
    }

    // ---------------------------------------------------------------- the tier-system switch

    /**
     * {@code tier_system} governs whether a tier <em>exists</em> for a stack, not whether it has
     * been taken away.
     *
     * <p>Both halves are asserted together because either alone is a trap: reading a rung while the
     * system is off would mean the switch does nothing, and erasing the rung would mean turning the
     * system back on could not bring it back.</p>
     */
    @Test
    void switchingTheTierSystemOffHidesEveryRungWithoutRemovingIt() {
        final ItemStack stack = atTier("gamma");
        final CompoundTag before = stack.getTag().copy();

        PolicyService.getInstance().applyResolved(new TierPolicy(false, true, true, true, true));

        assertTrue(this.service.getCurrentTier(stack, TierDomain.WEAPON).isEmpty(),
                "with the system off, the rung must not be readable");
        assertTrue(this.service.getAllCurrentTiers(stack).isEmpty(),
                "no ladder may answer while the system is off");
        assertEquals(before, stack.getTag(), "switching a system off must not destroy what it wrote");

        PolicyService.getInstance().applyResolved(TierPolicy.defaults());

        assertEquals(TestTiers.id("gamma"), this.service.getCurrentTier(stack, TierDomain.WEAPON).orElseThrow().id(),
                "handing the switch back must recover the rung from the untouched tag");
    }

    @Test
    void switchingTheTierSystemOffDoesNotTouchTheLevelSystem() {
        PolicyService.getInstance().applyResolved(new TierPolicy(false, true, true, true, true));
        try {
            assertTrue(PolicyService.getInstance().get().levellingSystem(),
                    "the level system is independent and must keep its own switch");
        } finally {
            PolicyService.getInstance().applyResolved(TierPolicy.defaults());
        }
    }

    // ---------------------------------------------------------------- downgrade

    /**
     * Downgrades are their own switch and no recipe route is generated for them yet.
     *
     * <p>Pinned as a no-op rather than a crash: a caller that has not learned the feature is live
     * should not take the game with it, and the attempt must leave the item exactly where it was.
     * Anything else would mean the disable is only partial and a re-enable is silently already
     * done.</p>
     */
    @Test
    void downgradingIsNotYetARouteAndLeavesTheItemExactlyAsItWas() {
        final ItemStack atBeta = atTier("beta");

        final ItemStack downgraded = this.service.downgrade(atBeta, TierDomain.WEAPON);

        assertEquals(TestTiers.id("beta"), this.service.getCurrentTier(downgraded).orElseThrow().id(),
                "an attempt must not move the item");
        assertNotSame(atBeta, downgraded, "the caller is handed a copy, never the stack it passed in");
    }

    @Test
    void downgradingAnItemWithNoDomainIsACopyAndLeavesItAlone() {
        final ItemStack stack = new ItemStack(Items.POTATO);

        final ItemStack result = this.service.downgrade(stack, TierDomain.WEAPON);

        assertEquals(Items.POTATO, result.getItem());
        assertTrue(this.service.getCurrentTier(result).isEmpty());
    }

    @Test
    void downgradingFromTheBottomOfTheLadderDoesNothing() {
        final ItemStack stack = atTier("alpha");

        assertEquals(TestTiers.id("alpha"),
                this.service.getCurrentTier(this.service.downgrade(stack, TierDomain.WEAPON)).orElseThrow().id());
    }

    // ---------------------------------------------------------------- description

    @Test
    void describeStackNamesTheItemEvenWhenItHasNoTier() {
        final String described = this.service.describeStack(new ItemStack(Items.IRON_SWORD)).getString();

        assertTrue(described.contains("Iron Sword"), described);
    }

    @Test
    void describeStackSummarisesEveryLadderTheItemSitsOn() {
        final String described = this.service.describeStack(atTier("alpha")).getString();

        assertTrue(described.contains("Iron Sword"), described);
        assertTrue(described.contains("tier=" + TestTiers.id("alpha")), described);
    }
}
