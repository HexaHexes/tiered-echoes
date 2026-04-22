package org.bitwisemadness.tieredechoes.common.path;

import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;

@UtilityClass
public class DisplayBranchResolver {

    public static String branchId(final ResourceLocation from, final ResourceLocation to, final int registrationOrder) {
        final String seed = from + "|" + to + "|" + registrationOrder;
        return "branch_" + Integer.toHexString(seed.hashCode());
    }
}
