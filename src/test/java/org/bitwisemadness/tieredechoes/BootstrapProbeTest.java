package org.bitwisemadness.tieredechoes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.testutil.TestBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TestBootstrap} actually initialises registries in a headless JVM.
 *
 * <p>Whether this works is not something the build can tell us in advance — if {@code bootStrap}
 * needs game state that a test run does not have, every registry-dependent test fails. Failures
 * here mean "rethink the harness", not "rethink the mod".</p>
 */
class BootstrapProbeTest {

    @Test
    void bootStrapYieldsUsableRegistries() {
        TestBootstrap.init();

        assertEquals(Items.DIAMOND_SWORD, BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("minecraft", "diamond_sword")));

        final ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(7);
        assertEquals(7, stack.getDamageValue());
        assertTrue(stack.getItem() instanceof net.minecraft.world.item.PickaxeItem);
    }
}
