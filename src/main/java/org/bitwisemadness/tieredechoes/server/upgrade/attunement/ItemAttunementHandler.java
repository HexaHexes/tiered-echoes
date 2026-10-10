package org.bitwisemadness.tieredechoes.server.upgrade.attunement;

import lombok.experimental.UtilityClass;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;
import org.bitwisemadness.tieredechoes.common.util.PlayerMessageUtil;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
@UtilityClass
public class ItemAttunementHandler {

    @SubscribeEvent
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        final ItemStack mainHand = event.getItemStack();
        final ItemStack offhand = event.getEntity().getOffhandItem();
        if (mainHand.isEmpty() || offhand.isEmpty()) {
            return;
        }
        if (UpgradeService.getInstance().attuneStack(mainHand, offhand)) {
            // The datapack policy wins over the config file when it states a value; see TierPolicy.
            if (PolicyService.getInstance().consumeAttunementItem() && !event.getEntity().getAbilities().instabuild) {
                offhand.shrink(1);
            }
            PlayerMessageUtil.actionBar(event.getEntity(), net.minecraft.network.chat.Component.literal("Attuned path updated."));
            event.setCanceled(true);
        }
    }
}
