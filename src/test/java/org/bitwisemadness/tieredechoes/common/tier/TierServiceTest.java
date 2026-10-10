package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.common.data.loader.ShippedDatapack;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
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
        te.putString("tier_id", TestTiers.id("common").toString());
        te.putString("tier_domain", "armor");

        assertEquals(TierDomain.ARMOR, this.service.readTierFromNbt(stack, null).orElseThrow().domain());
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

        final Optional<TierDefinition> tier = this.service.resolveCurrentTier(new ItemStack(Items.IRON_SWORD), null);

        assertEquals(TestTiers.id("common"), tier.orElseThrow().id());
    }

    @Test
    void itemsThatCannotCarryADomainGetNoTierAtAll() {
        this.service.replaceAll(List.of(TestTiers.tier("common", TierDomain.WEAPON, 0, 0)));

        assertTrue(this.service.resolveCurrentTier(new ItemStack(Items.POTATO), null).isEmpty(),
                "a potato is not a weapon, tool or armour piece");
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
        assertEquals(16, this.service.getAllTiers().size());
        assertEquals(new ResourceLocation("tieredechoes", "common"),
                this.service.getTier(new ResourceLocation("tieredechoes", "common"), TierDomain.TOOL).orElseThrow().id());
    }
}
