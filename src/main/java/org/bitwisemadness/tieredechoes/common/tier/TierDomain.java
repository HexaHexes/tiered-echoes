package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.util.StringRepresentable;

public enum TierDomain implements StringRepresentable {
    WEAPON("weapon"),
    TOOL("tool"),
    ARMOR("armor"),
    SHIELD("shield"),
    RANGED_WEAPONS("ranged_weapons"),
    ELYTRA("elytra"),
    ACCESSORY("accessory");

    private final String serializedName;

    TierDomain(final String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Domains that ship with a built-in tier ladder. Both {@code TierService} and {@code PathService}
     * bootstrap from this list so that every built-in edge has matching built-in endpoints — a
     * mismatch would show up as {@code missing_source_tier} validation errors on every load.
     *
     * <p>Membership is decided by the {@code tieredechoes:<name>} item tags rather than by item
     * class, so an item belongs to a domain because a tag says so and an item in two tags belongs to
     * two domains with a ladder for each. Adding an item to a tag is therefore the whole of making it
     * tierable, and a datapack can do that for any item without a line of Java.</p>
     *
     * <p>Only the three domains whose items carry attributes in vanilla ship a ladder. A shield, an
     * elytra and a bow scale durability and nothing else, so a built-in ladder for them would be a
     * durability table with a different name on it; and accessories are deferred until curios support
     * is designed properly rather than approximated. Both remain full members of the enum with their
     * tag mappings intact, so a datapack that wants a shield or accessory ladder can still declare
     * one — the mod simply does not ship the JSON for it.</p>
     */
    public static java.util.List<TierDomain> builtinDomains() {
        return java.util.List.of(WEAPON, TOOL, ARMOR);
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    /**
     * Resolves a domain from either its serialized name ({@code weapon}) or its enum constant
     * ({@code WEAPON}), matching case-insensitively so commands and JSON both read naturally.
     */
    public static java.util.Optional<TierDomain> byName(final String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        for (final TierDomain domain : values()) {
            if (domain.serializedName.equalsIgnoreCase(name) || domain.name().equalsIgnoreCase(name)) {
                return java.util.Optional.of(domain);
            }
        }
        return java.util.Optional.empty();
    }
}
