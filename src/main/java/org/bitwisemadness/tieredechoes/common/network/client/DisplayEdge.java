package org.bitwisemadness.tieredechoes.common.network.client;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.Optional;

/**
 * The slice of a {@code TierEdge} a client needs to render an experience tooltip.
 *
 * <p>Deliberately not the full record. A {@code TierEdge} also carries the attunement and upgrade
 * ingredients, which the client has no use for and which are server-side gameplay data; sending
 * them would put recipe internals on the wire for no benefit. The experience requirement is the
 * one field the tooltip has to quote.</p>
 */
public record DisplayEdge(
        ResourceLocation from,
        ResourceLocation to,
        TierDomain domain,
        Optional<String> displayBranchId,
        Optional<Integer> experienceRequirementOverride
) {
    public DisplayEdge {
        displayBranchId = displayBranchId == null ? Optional.empty() : displayBranchId;
        experienceRequirementOverride =
                experienceRequirementOverride == null ? Optional.empty() : experienceRequirementOverride;
    }
}
