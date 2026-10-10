package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.util.Optional;

/**
 * Lookups the GameTests share.
 *
 * <p>Every helper that can fail reports through {@link GameTestHelper}, so a stale ladder shows up
 * as a named test failure rather than as a {@code NoSuchElementException} with no context.</p>
 */
final class GameTestTiers {

    private GameTestTiers() {
    }

    static ResourceLocation tierId(final String path) {
        return new ResourceLocation(TieredEchoes.MOD_ID, path);
    }

    static TierDefinition tier(final GameTestHelper helper, final TierDomain domain, final String path) {
        final Optional<TierDefinition> tier = TierService.getInstance().getTier(tierId(path), domain);
        if (tier.isEmpty()) {
            helper.fail("No " + domain.getSerializedName() + " tier named " + tierId(path) + " is loaded");
        }
        return tier.get();
    }

    /** A freshly placed iron sword carrying the given tier and nothing else. */
    static ItemStack tieredSword(final GameTestHelper helper, final String path) {
        final ItemStack stack = new ItemStack(Items.IRON_SWORD);
        // applyTier is functional: it hands back the tiered copy and leaves the argument alone.
        return UpgradeService.getInstance().applyTier(stack, tier(helper, TierDomain.WEAPON, path));
    }
}
