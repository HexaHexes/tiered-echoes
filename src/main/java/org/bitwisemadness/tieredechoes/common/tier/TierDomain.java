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

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
