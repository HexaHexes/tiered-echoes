package org.bitwisemadness.tieredechoes.common.network.client;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.ArrayList;
import java.util.List;

/**
 * Progression state a client holds for display purposes.
 *
 * <p>Two things live here, and they are here for the same reason: vanilla has no way to give a
 * client either of them.</p>
 *
 * <p>The transition graph is server-side data that the client is never told about, because the
 * client cannot act on ingredients. The tooltip is the first thing that needs a number from it,
 * so the display-relevant slice is now sent.</p>
 *
 * <p>The gamerules are the other half. Minecraft 1.20.1 has no gamerule sync packet at all;
 * {@code ClientLevelData} is constructed from a difficulty and two flags, so {@code
 * Level#getGameRules()} on a client returns a locally built default rather than the server's
 * rules. Any value the tooltip quotes has to arrive over this mod's own channel.</p>
 *
 * <p>Kept separate from {@code PathService} on purpose. That service holds the full edges and is
 * shared with the server process, so in singleplayer a client writing its reduced copy into it
 * would quietly destroy the server's attunement ingredients.</p>
 */
public final class SyncedProgressionState {

    private static volatile boolean itemExperienceEnabled = true;
    private static volatile boolean autoUpgradeEnabled = true;
    private static volatile boolean resetExperienceOnUpgrade = false;
    private static volatile List<DisplayEdge> edges = List.of();

    private SyncedProgressionState() {
    }

    public static void apply(final boolean itemExperience, final boolean autoUpgrade,
                             final boolean resetOnUpgrade, final List<DisplayEdge> syncedEdges) {
        itemExperienceEnabled = itemExperience;
        autoUpgradeEnabled = autoUpgrade;
        resetExperienceOnUpgrade = resetOnUpgrade;
        edges = List.copyOf(syncedEdges);
    }

    public static boolean itemExperienceEnabled() {
        return itemExperienceEnabled;
    }

    public static boolean autoUpgradeEnabled() {
        return autoUpgradeEnabled;
    }

    public static boolean resetExperienceOnUpgrade() {
        return resetExperienceOnUpgrade;
    }

    /** Outgoing edges from a tier, in the order the datapack declared them. */
    public static List<DisplayEdge> nextEdges(final ResourceLocation tierId, final TierDomain domain) {
        final List<DisplayEdge> result = new ArrayList<>(2);
        for (final DisplayEdge edge : edges) {
            if (edge.domain() == domain && edge.from().equals(tierId)) {
                result.add(edge);
            }
        }
        return result;
    }
}
