package org.bitwisemadness.tieredechoes.api.common;

import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

/**
 * The progression operations an item can undergo.
 *
 * <p>Every method is ladder-scoped. An item belongs to a domain because a tag says so, and an item
 * in two tags belongs to two domains — so an axe is both a weapon and a tool and climbs each ladder
 * independently. A call therefore names the ladder it means, and never reaches another one.</p>
 */
public interface IUpgradeService {
    ItemStack applyPreviewTier(ItemStack stack, TierDefinition tier);

    ItemStack applyTier(ItemStack stack, TierDefinition tier);

    /**
     * Steps the named ladder back one rung. The ladder comes from the caller rather than being
     * inferred, because an item can hold a rung on several and dropping the weapon rung of an axe
     * must not also drop its tool rung.
     */
    ItemStack downgrade(ItemStack stack, TierDomain domain);
}
