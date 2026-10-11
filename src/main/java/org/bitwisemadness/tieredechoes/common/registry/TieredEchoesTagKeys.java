package org.bitwisemadness.tieredechoes.common.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One item tag per {@code TierDomain}, and the only place the domain-to-tag mapping lives.
 *
 * <p>These tags are the membership rule for the whole progression system, not just a recipe
 * constraint. An item belongs to a domain because its tag contains it, so a datapack makes any item
 * tierable by adding it to a tag rather than by writing a recipe. An item in two tags belongs to two
 * domains and climbs a ladder for each.</p>
 *
 * <p>Each tag ships with vanilla or Forge conventions as its contents so that modded gear is picked
 * up without a datapack: {@code #forge:armors} covers any mod following Forge's armour convention,
 * and {@code #forge:tools/shields} likewise for shields.</p>
 */
public final class TieredEchoesTagKeys {

    private static final Map<TierDomain, TagKey<Item>> BY_DOMAIN = new EnumMap<>(TierDomain.class);

    /**
     * The tag path for each domain. Spelled out rather than derived, because pluralising is not
     * mechanical: {@code armor}, {@code elytra} and {@code ranged_weapons} are already plural, and
     * blindly appending {@code s} to the last of those produces {@code ranged_weaponss}.
     */
    private static final Map<TierDomain, String> TAG_PATHS = Map.of(
            TierDomain.WEAPON, "weapons",
            TierDomain.TOOL, "tools",
            TierDomain.ARMOR, "armor",
            TierDomain.SHIELD, "shields",
            TierDomain.RANGED_WEAPONS, "ranged_weapons",
            TierDomain.ELYTRA, "elytra",
            TierDomain.ACCESSORY, "accessories");

    static {
        for (final TierDomain domain : TierDomain.builtinDomains()) {
            final String path = TAG_PATHS.get(domain);
            if (path == null) {
                throw new IllegalStateException("TierDomain " + domain + " has no item tag path");
            }
            BY_DOMAIN.put(domain, item(path));
        }
    }

    private TieredEchoesTagKeys() {
    }

    public static TagKey<Item> item(final String path) {
        return ItemTags.create(new ResourceLocation(TieredEchoes.MOD_ID, path));
    }

    /**
     * The tag whose membership grants an item a ladder in this domain, or empty for a domain that
     * ships no ladder.
     */
    public static Optional<TagKey<Item>> forDomain(final TierDomain domain) {
        return Optional.ofNullable(BY_DOMAIN.get(domain));
    }
}
