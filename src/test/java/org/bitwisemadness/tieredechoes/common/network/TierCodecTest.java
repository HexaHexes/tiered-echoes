package org.bitwisemadness.tieredechoes.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;
import org.bitwisemadness.tieredechoes.common.tier.IngredientMatcher;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.testutil.TestTiers;
import org.junit.jupiter.api.Test;

/**
 * {@link TierCodec} is what a client sees of the ladder. A field written out of order, or a missing
 * presence flag, desynchronises every subsequent read — the failure mode is not a wrong value but a
 * scrambled packet, so round-tripping the whole record is the only assertion that catches it.
 */
class TierCodecTest {

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    }

    private static TierDefinition fullyPopulatedTier() {
        return new TierDefinition(
                TestTiers.id("rich"),
                TierDomain.ARMOR,
                3,
                Rarity.EPIC,
                7,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "diamond")),
                Optional.of(new IngredientMatcher(IngredientMatcher.Kind.TAG, new ResourceLocation("minecraft", "ingots"))),
                Optional.of(TestTiers.id("previous")),
                Optional.of("testpack"),
                Map.of("attack_damage", 1.25D),
                Map.of("*3", 1.15D),
                Map.of("apotheosis:affix", 2.0D),
                true,
                false,
                Optional.of(new ResourceLocation("tieredechoes", "models/item/rich_upgrade_template")),
                Optional.empty(),
                Optional.of(new ResourceLocation("tieredechoes", "textures/item/rich_upgrade_template")),
                Optional.empty());
    }

    @Test
    void tierSurvivesAWriteReadCycleFieldForField() {
        final TierDefinition original = fullyPopulatedTier();
        final FriendlyByteBuf buf = buffer();

        TierCodec.writeTier(buf, original);
        final TierDefinition copy = TierCodec.readTier(buf);

        assertEquals(original, copy);
    }

    @Test
    void optionalFieldsSurviveBothTheirEmptyAndPresentForms() {
        final TierDefinition minimal = new TierDefinition(
                TestTiers.id("plain"), TierDomain.WEAPON, 1, Rarity.COMMON, 0,
                new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "stick")),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Map.of(), Map.of(), Map.of(), false, true);
        final FriendlyByteBuf buf = buffer();

        TierCodec.writeTier(buf, minimal);

        assertEquals(minimal, TierCodec.readTier(buf));
    }

    @Test
    void tierListKeepsItsOrderAndSize() {
        final List<TierDefinition> tiers = List.of(fullyPopulatedTier(),
                new TierDefinition(TestTiers.id("second"), TierDomain.TOOL, 1, Rarity.RARE, 1,
                        new IngredientMatcher(IngredientMatcher.Kind.ITEM, new ResourceLocation("minecraft", "iron_ingot")),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Map.of(), Map.of(), Map.of(), true, true));
        final FriendlyByteBuf buf = buffer();

        TierCodec.writeTiers(buf, tiers);

        assertEquals(tiers, TierCodec.readTiers(buf));
    }

    /** Guards the allocation cap: a desynced or hostile server must not be able to force 2^31 reads. */
    @Test
    void tierListRejectsAnImplausibleDeclaredSize() {
        final FriendlyByteBuf buf = buffer();
        buf.writeVarInt(-1);

        assertThrows(IllegalArgumentException.class, () -> TierCodec.readTiers(buf));

        final FriendlyByteBuf tooBig = buffer();
        tooBig.writeVarInt(1_000_000);

        assertThrows(IllegalArgumentException.class, () -> TierCodec.readTiers(tooBig));
    }

    /** Decode must fail loudly on a short buffer rather than synthesising a half-populated tier. */
    @Test
    void truncatedBufferFailsInsteadOfDecodingGarbage() {
        final FriendlyByteBuf full = buffer();
        TierCodec.writeTier(full, fullyPopulatedTier());
        final FriendlyByteBuf truncated = new FriendlyByteBuf(full.copy(0, Math.max(1, full.readableBytes() / 4)));

        assertThrows(IndexOutOfBoundsException.class, () -> TierCodec.readTier(truncated));
    }

    /**
     * All thirty-two combinations of the five switches.
     *
     * <p>Exhaustive rather than representative because the codec writes five bits in a fixed order
     * with no presence flags, so a single swapped pair is invisible in any sample that does not
     * vary both of the fields it swapped.</p>
     */
    @Test
    void policyRoundTripsEveryCombinationOfSwitches() {
        int tested = 0;
        for (int bits = 0; bits < 32; bits++) {
            final TierPolicy original = new TierPolicy(
                    (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
            final FriendlyByteBuf buf = buffer();

            TierCodec.writePolicy(buf, original);

            assertEquals(original, TierCodec.readPolicy(buf), "bits " + Integer.toBinaryString(bits));
            tested++;
        }
        assertEquals(32, tested, "every combination should have been exercised");
    }

    /** An explicit false must survive: "the switch is off" and "no switch was declared" differ. */
    @Test
    void policyDistinguishesAnExplicitFalseFromNoStatementAtAll() {
        final TierPolicy allOff = new TierPolicy(false, false, false, false, false);
        final TierPolicy undeclared = TierPolicy.defaults();
        final FriendlyByteBuf first = buffer();
        final FriendlyByteBuf second = buffer();

        TierCodec.writePolicy(first, allOff);
        TierCodec.writePolicy(second, undeclared);

        assertEquals(allOff, TierCodec.readPolicy(first));
        assertEquals(undeclared, TierCodec.readPolicy(second));
    }
}
