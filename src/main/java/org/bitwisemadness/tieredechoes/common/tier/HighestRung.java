package org.bitwisemadness.tieredechoes.common.tier;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/**
 * Picks which ladder rung an item displays when it holds more than one.
 *
 * <p>An axe climbs both the weapon and the tool ladder, so it can sit on two rungs at once. The
 * {@code [Tier]} suffix and the name's rarity colour both have to agree on which one wins, or an
 * item could read {@code [Epic]} in rare purple. Both mixins ask this class rather than each
 * keeping its own copy of the rule — they had one, and the rarity copy had silently rotted into
 * reading a path nothing ever writes.</p>
 *
 * <p>The highest {@code tier_sort_order} wins, because that number is the rung's position in its
 * ladder and is what the rest of the mod already treats as rank. Ties fall back to the ladder's
 * declaration order in {@link TierDomain}: without that the winner would follow NBT iteration
 * order, which is not stable between two otherwise identical items.</p>
 *
 * <p>Read from NBT directly rather than through {@link TierService}, because this is the render
 * path for every item tooltip. The ladder's own sort order is already written into each domain
 * compound, so no registry lookup is needed to know which rung is highest.</p>
 */
public final class HighestRung {

    private HighestRung() {
    }

    /**
     * The domain compound holding the highest rung this item has reached on any ladder, or empty
     * when it has reached none.
     *
     * <p>A ladder entry with no {@code tier_id} is skipped rather than winning by accident: an
     * entry that cannot be named is one that has not been written yet.</p>
     */
    public static Optional<CompoundTag> of(final CompoundTag domains) {
        CompoundTag best = null;
        int bestOrder = Integer.MIN_VALUE;
        int bestDomainRank = Integer.MAX_VALUE;
        for (final String key : domains.getAllKeys()) {
            final CompoundTag entry = domains.getCompound(key);
            if (entry.getString(TierService.TIER_ID_TAG).isEmpty()) {
                continue;
            }
            final int order = entry.getInt(TierService.SORT_ORDER_TAG);
            final int domainRank = domainRank(key);
            if (order > bestOrder || (order == bestOrder && domainRank < bestDomainRank)) {
                best = entry;
                bestOrder = order;
                bestDomainRank = domainRank;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A domain's declaration order, used only to break ties between equally-ranked rungs. An unknown
     * domain sorts last rather than being skipped, so a ladder written by a newer build still
     * displays instead of vanishing from the name.
     */
    private static int domainRank(final String key) {
        final TierDomain[] values = TierDomain.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].getSerializedName().equalsIgnoreCase(key)) {
                return i;
            }
        }
        return values.length;
    }
}
