package org.bitwisemadness.tieredechoes.common.tier;

import net.minecraft.util.StringRepresentable;

public enum TierDomain implements StringRepresentable {
    WEAPON("weapon"),
    TOOL("tool"),
    ARMOR("armor"),
    ELYTRA("elytra"),
    ACCESSORY("accessory"),
    STORAGE("storage");

    private final String serializedName;

    TierDomain(final String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Domains that ship with a built-in tier ladder. Both {@code TierService} and {@code PathService}
     * bootstrap from this list so that every built-in edge has matching built-in endpoints — a
     * mismatch would show up as {@code missing_source_tier} validation errors on every load.
     */
    public static java.util.List<TierDomain> builtinDomains() {
        return java.util.List.of(WEAPON, TOOL, ARMOR, ACCESSORY);
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
