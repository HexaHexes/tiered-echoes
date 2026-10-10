package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.resources.ResourceLocation;

/**
 * Composite lookup key for tiers and path nodes.
 *
 * <p>Tier ids are intentionally <em>not</em> globally unique: {@code tieredechoes:common} exists once
 * per {@link TierDomain}. Every map that resolves a tier by id must therefore be keyed by
 * {@code (domain, id)} or the last inserted domain silently wins.</p>
 */
public record TierKey(TierDomain domain, ResourceLocation id) {
    public static TierKey of(final TierDefinition tier) {
        return new TierKey(tier.domain(), tier.id());
    }
}
