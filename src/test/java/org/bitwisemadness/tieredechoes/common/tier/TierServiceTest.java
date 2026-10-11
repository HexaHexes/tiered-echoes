package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.common.data.loader.ShippedDatapack;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.bitwisemadness.tieredechoes.testutil.TestTags;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierServiceTest {

    private TierService service;

    @BeforeEach
    void setUp() {
        TestBootstrap.init();
        this.service = TierService.getInstance();
    }

    @AfterEach
    void tearDown() {
        // Tag bindings are static and registry-wide, so a binding left behind by one test would make
        // the next one see an item as tierable when it is not.
        TestTags.clear();
        ShippedDatapack.restoreShippedState();
    }

    @Test
    void shippedLadderGivesEveryBuiltinDomainAFourStepLadder() {
        final List<TierDefinition> shipped = ShippedDatapack.load().tiers();

        assertEquals(TierDomain.builtinDomains().size() * 4, shipped.size());
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final List<TierDefinition> forDomain = shipped.stream().filter(tier -> tier.domain() == domain).toList();
            assertEquals(4, forDomain.size(), domain + " should ship common/uncommon/rare/epic");
        }
    }

    /** There is no Java fallback any more, so a service that never reloaded really is empty. */
    @Test
    void anUnloadedServiceHoldsNoTiersRatherThanACopyOfTheJar() {
        this.service.replaceAll(List.of());

        assertTrue(this.service.getTier(TestTiers.id("common"), TierDomain.WEAPON).isEmpty());
    }

    @Test
    void tierIdsAreResolvedWithinADomainRatherThanGlobally() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("common", TierDomain.ARMOR, 0, 0)));

        final Optional<TierDefinition> weapon = this.service.getTier(TestTiers.id("common"), TierDomain.WEAPON);
        final Optional<TierDefinition> armor = this.service.getTier(TestTiers.id("common"), TierDomain.ARMOR);

        assertEquals(TierDomain.WEAPON, weapon.orElseThrow().domain());
        assertEquals(TierDomain.ARMOR, armor.orElseThrow().domain());
        assertNotEquals(weapon, armor);
    }

    /** The API-compatible unscoped lookup must not silently pick one domain's winner. */
    @Test
    void unscopedLookupRefusesToGuessWhenTheIdIsAmbiguous() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("common", TierDomain.ARMOR, 0, 0)));

        assertEquals(Optional.empty(), this.service.getTier(TestTiers.id("common")));
    }

    @Test
    void unscopedLookupStillWorksWhenOnlyOneDomainDeclaresTheId() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));

        assertEquals(TierDomain.WEAPON, this.service.getTier(TestTiers.id("common")).orElseThrow().domain());
    }

    @Test
    void orderedTiersAreSortedBySortOrderNotFileOrder() {
        this.service.replaceAll(List.of(
                TestTiers.tier("epic", TierDomain.WEAPON, 3, 150),
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("rare", TierDomain.WEAPON, 2, 75)));

        final List<TierDefinition> ordered = this.service.getOrderedTiers(TierDomain.WEAPON);

        assertEquals(List.of("common", "rare", "epic"), ordered.stream().map(tier -> tier.id().getPath()).toList());
    }

    @Test
    void nullLookupsAreEmptyRatherThanThrowing() {
        assertEquals(Optional.empty(), this.service.getTier(null, TierDomain.WEAPON));
        assertEquals(Optional.empty(), this.service.getTier(TestTiers.id("common"), null));
        assertEquals(Optional.empty(), this.service.getTier(null));
    }

    @Test
    void storedTierIsReadBackWithItsDomain() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("common", TierDomain.ARMOR, 0, 0)));
        final ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        final CompoundTag te = stack.getOrCreateTagElement("TieredEchoes");
        final CompoundTag domains = new CompoundTag();
        final CompoundTag armor = new CompoundTag();
        armor.putString("tier_id", TestTiers.id("common").toString());
        domains.put("armor", armor);
        te.put("domains", domains);

        assertEquals(TierDomain.ARMOR, this.service.readTierFromNbt(stack, TierDomain.ARMOR).orElseThrow().domain());
    }

    /**
     * An axe belongs to two tags and so holds a rung on two ladders. Each is stored under its own
     * key so that neither overwrites the other, and both must come back independently.
     */
    @Test
    void twoLaddersOnOneItemAreStoredAndReadBackIndependently() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("common", TierDomain.TOOL, 0, 0)));
        final ItemStack stack = new ItemStack(Items.IRON_AXE);
        final CompoundTag te = stack.getOrCreateTagElement("TieredEchoes");
        final CompoundTag domains = new CompoundTag();
        final CompoundTag weapon = new CompoundTag();
        weapon.putString("tier_id", TestTiers.id("common").toString());
        final CompoundTag tool = new CompoundTag();
        tool.putString("tier_id", TestTiers.id("common").toString());
        domains.put("weapon", weapon);
        domains.put("tool", tool);
        te.put("domains", domains);

        assertEquals(2, this.service.readAllTiersFromNbt(stack).size(),
                "an item in two tags must carry a rung for each ladder");
        assertEquals(TierDomain.WEAPON, this.service.readTierFromNbt(stack, TierDomain.WEAPON).orElseThrow().domain());
        assertEquals(TierDomain.TOOL, this.service.readTierFromNbt(stack, TierDomain.TOOL).orElseThrow().domain());
    }

    /** A ladder whose domain the datapack has since removed must not discard the item's other rungs. */
    @Test
    void aLadderForARemovedDomainIsSkippedRatherThanDiscardingTheItem() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));
        final ItemStack stack = new ItemStack(Items.IRON_AXE);
        final CompoundTag te = stack.getOrCreateTagElement("TieredEchoes");
        final CompoundTag domains = new CompoundTag();
        final CompoundTag weapon = new CompoundTag();
        weapon.putString("tier_id", TestTiers.id("common").toString());
        final CompoundTag unknown = new CompoundTag();
        unknown.putString("tier_id", TestTiers.id("common").toString());
        domains.put("weapon", weapon);
        domains.put("not_a_real_domain", unknown);
        te.put("domains", domains);

        assertEquals(1, this.service.readAllTiersFromNbt(stack).size(),
                "the unrecognised ladder should be skipped, not take the recognised one with it");
    }

    @Test
    void aTierIdThatNoLongerExistsResolvesToNothingSoTheItemIsNotMislabeled() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);
        stack.getOrCreateTagElement("TieredEchoes").putString("tier_id", TestTiers.id("vanished").toString());

        assertEquals(Optional.empty(), this.service.resolveCurrentTier(stack, null));
    }

    @Test
    void anUntieredStackFallsBackToTheFirstTierTheItemCanCarry() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("uncommon", TierDomain.WEAPON, 1, 25)));
        TestTags.bindDomain(TierDomain.WEAPON, Items.IRON_SWORD);

        final Optional<TierDefinition> tier = this.service.resolveCurrentTier(new ItemStack(Items.IRON_SWORD), null);

        assertEquals(TestTiers.id("common"), tier.orElseThrow().id());
    }

    /**
     * Domain membership is the tag and nothing else. An iron sword in no tag carries no domain, even
     * though it is a sword — which is the whole point of moving off the {@code instanceof} heuristic.
     */
    @Test
    void anItemInNoTagCarriesNoDomainWhateverItsClass() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));
        TestTags.clear();

        assertTrue(this.service.inferDomains(Items.IRON_SWORD).isEmpty(),
                "an iron sword outside every base tag is not a weapon");
        assertEquals(Optional.empty(), this.service.resolveCurrentTier(new ItemStack(Items.IRON_SWORD), null));
    }

    /**
     * The tag is the whole membership rule, so adding an item to it is the whole of making it
     * tierable — including items no {@code instanceof} heuristic would ever have matched.
     */
    @Test
    void anItemInTwoTagsBelongsToTwoDomains() {
        this.service.replaceAll(List.of(
                TestTiers.tier("common", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("common", TierDomain.TOOL, 0, 0)));
        TestTags.bindDomain(TierDomain.WEAPON, Items.IRON_AXE);
        TestTags.bindDomain(TierDomain.TOOL, Items.IRON_AXE);

        assertEquals(java.util.Set.of(TierDomain.WEAPON, TierDomain.TOOL), this.service.inferDomains(Items.IRON_AXE));
    }

    @Test
    void itemsThatCannotCarryADomainGetNoTierAtAll() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));
        TestTags.clear();

        assertTrue(this.service.resolveCurrentTier(new ItemStack(Items.POTATO), null).isEmpty(),
                "a potato is in no base tag, so it carries no domain");
    }

    @Test
    void previousTierFallsBackToListPositionWhenNoExplicitChainIsDeclared() {
        this.service.replaceAll(List.of(
                TestTiers.tier("a", TierDomain.WEAPON, 0, 0),
                TestTiers.tier("b", TierDomain.WEAPON, 1, 10),
                TestTiers.tier("c", TierDomain.WEAPON, 2, 20)));

        assertEquals(Optional.empty(), this.service.getPreviousTier(
                this.service.getTier(TestTiers.id("a"), TierDomain.WEAPON).orElseThrow()));
        assertEquals(TestTiers.id("a"), this.service.getPreviousTier(
                this.service.getTier(TestTiers.id("b"), TierDomain.WEAPON).orElseThrow()).orElseThrow().id());
    }

    @Test
    void shippedDataProducesTheExpectedLadderInEveryDomain() {
        ShippedDatapack.install();

        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final List<TierDefinition> ordered = this.service.getOrderedTiers(domain);
            assertEquals(List.of("common", "uncommon", "rare", "epic"),
                    ordered.stream().map(tier -> tier.id().getPath()).toList(), domain.toString());
        }
        assertEquals(TierDomain.builtinDomains().size() * 4, this.service.getAllTiers().size());
        assertEquals(new ResourceLocation("tieredechoes", "common"),
                this.service.getTier(new ResourceLocation("tieredechoes", "common"), TierDomain.TOOL).orElseThrow().id());
    }
}
