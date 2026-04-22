package org.bitwisemadness.tieredechoes.common.util;

import lombok.experimental.UtilityClass;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@UtilityClass
public class PlayerMessageUtil {
    public static void actionBar(final LivingEntity entity, final Component message) {
        if (!entity.level().isClientSide && entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }
}
