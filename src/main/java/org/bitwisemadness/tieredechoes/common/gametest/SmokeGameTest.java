package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;
import org.bitwisemadness.tieredechoes.TieredEchoes;

/**
 * Proves the GameTest harness itself is wired up before anything more interesting is asserted.
 *
 * <p>GameTests need a structure file per test and a run configuration that enables the namespace,
 * so a silent misconfiguration would otherwise show up as "no tests ran" rather than a failure.
 * This test gives the run one unmistakable green line to look for.</p>
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class SmokeGameTest {

    private SmokeGameTest() {
    }

    @GameTest
    public static void templateItemsAreRegistered(final GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.ITEMS.getValue(new ResourceLocation(TieredEchoes.MOD_ID, "common_upgrade_template")) != null,
                "the mod's smithing template items should be registered");
        helper.succeed();
    }
}
