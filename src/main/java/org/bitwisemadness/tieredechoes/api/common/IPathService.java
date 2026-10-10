package org.bitwisemadness.tieredechoes.api.common;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tieredechoes.common.path.TierEdge;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.List;
import java.util.Optional;

public interface IPathService {
    List<TierEdge> getOutgoingEdges(ResourceLocation tierId, TierDomain domain);

    Optional<TierEdge> getDowngradeEdge(ResourceLocation tierId, TierDomain domain);
}
