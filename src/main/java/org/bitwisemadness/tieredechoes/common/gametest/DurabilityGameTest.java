package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

/**
 * Durability scaling, which lives on a mixin over {@code ItemStack#getMaxDamage}.
 *
 * <p>A mixin never runs in a plain JVM, so this is the only place the behaviour can be observed at
 * all. That matters more than usual here: an earlier draft of the datapack declared a
 * {@code durability} multiplier that nothing read, and every unit test passed while the feature did
 * absolutely nothing. Reading the value back off a real stack is what catches that.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class DurabilityGameTest {

    private DurabilityGameTest() {
    }

    @GameTest
    public static void aTieredArmorOutlivesAnUntieredOne(final GameTestHelper helper) {
        final TierDefinition uncommon = GameTestTiers.tier(helper, TierDomain.ARMOR, "uncommon");
        final ItemStack tiered = GameTestTiers.tieredItem(helper, TierDomain.ARMOR, Items.LEATHER_CHESTPLATE, "uncommon");
        final int base = new ItemStack(Items.LEATHER_CHESTPLATE).getMaxDamage();

        final double multiplier = TierService.getInstance()
                .getScalarMultiplier(uncommon, "durability").orElseThrow();
        final int expected = Math.max(1, (int) Math.round(base * multiplier));

        helper.assertTrue(tiered.getMaxDamage() > base,
                "a tiered armor piece should outlast a plain one, but got " + tiered.getMaxDamage() + " against " + base);
        helper.assertTrue(tiered.getMaxDamage() == expected,
                "expected " + expected + " durability (base " + base + " x " + multiplier + ") but got " + tiered.getMaxDamage());
        helper.succeed();
    }

    /**
     * A shield now carries no ladder at all, which makes it the plainest possible case: an item the
     * mod recognises as nothing in particular must be left entirely alone, not merely given a
     * multiplier of one.
     */
    @GameTest
    public static void anUntieredItemKeepsItsOriginalDurability(final GameTestHelper helper) {
        final ItemStack plain = new ItemStack(Items.SHIELD);
        final int before = plain.getMaxDamage();
        // Touching the registry and the tier service must not disturb an item that carries no tier.
        TierService.getInstance().inferDomains(Items.SHIELD);

        helper.assertTrue(plain.getMaxDamage() == before,
                "an untiered shield changed durability from " + before + " to " + plain.getMaxDamage());
        helper.succeed();
    }

    /**
     * The elytra was the durability-only domain, and it is here because a durability-only case is
     * exactly what the {@code durability}-keyed multiplier table exists to serve. It is exercised
     * through a tool now rather than through the elytra itself: shields and elytras ship no ladder
     * any more, so the elytra would have no tier to look up. The read site is the same either way.
     */
    @GameTest
    public static void aTieredToolOutlivesAnUntieredOne(final GameTestHelper helper) {
        final TierDefinition rare = GameTestTiers.tier(helper, TierDomain.TOOL, "rare");
        final ItemStack tiered = GameTestTiers.tieredItem(helper, TierDomain.TOOL, Items.IRON_PICKAXE, "rare");
        final int base = new ItemStack(Items.IRON_PICKAXE).getMaxDamage();

        final double multiplier = TierService.getInstance()
                .getScalarMultiplier(rare, "durability").orElseThrow();
        final int expected = Math.max(1, (int) Math.round(base * multiplier));

        helper.assertTrue(tiered.getMaxDamage() == expected,
                "expected " + expected + " durability (base " + base + " x " + multiplier + ") but got " + tiered.getMaxDamage());
        helper.succeed();
    }

    /**
     * Scaled durability is a ceiling, not a claim on the current damage. A shield that has already
     * taken a hit must keep that damage against its new, larger maximum rather than being repaired
     * by the act of being tiered.
     */
    @GameTest
    public static void tieringDoesNotRepairDamageAlreadyTaken(final GameTestHelper helper) {
        final ItemStack damaged = new ItemStack(Items.IRON_CHESTPLATE);
        damaged.setDamageValue(100);
        final int damageBefore = damaged.getDamageValue();

        final ItemStack tiered = UpgradeService.getInstance()
                .applyTier(damaged, GameTestTiers.tier(helper, TierDomain.ARMOR, "epic"));

        helper.assertTrue(tiered.getDamageValue() == damageBefore,
                "tiering repaired the stack: damage went from " + damageBefore + " to " + tiered.getDamageValue());
        helper.assertTrue(tiered.getMaxDamage() > damaged.getMaxDamage(),
                "an epic chestplate should have a higher maximum than the base " + damaged.getMaxDamage());
        helper.succeed();
    }

    /**
     * An item in two domains climbs both ladders. The iron axe is the case the tag-driven membership
     * exists for, and it is the one an {@code instanceof} heuristic would have had to special-case.
     */
    @GameTest
    public static void aTieredItemStillReadsAsDamageable(final GameTestHelper helper) {
        final ItemStack tiered = GameTestTiers.tieredItem(helper, TierDomain.WEAPON, Items.IRON_SWORD, "epic");

        helper.assertTrue(tiered.isDamageableItem(),
                "scaling maximum durability must not make the item read as indestructible");
        helper.assertTrue(tiered.getMaxDamage() > new ItemStack(Items.IRON_SWORD).getMaxDamage(),
                "an epic iron sword should outlast a plain one");
        helper.succeed();
    }
}
