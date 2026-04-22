package org.bitwisemadness.tieredechoes.common.experience;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;
import org.bitwisemadness.tieredechoes.common.util.PlayerMessageUtil;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
public final class ItemExperienceHandler {
    private ItemExperienceHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(final AttackEntityEvent event) {
        if (!TieredEchoesConfig.ENABLE_ITEM_XP.get()) {
            return;
        }
        final ItemStack mainHand = event.getEntity().getMainHandItem();
        if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.WEAPON)) {
            UpgradeService.getInstance().addExperience(mainHand, 1);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!TieredEchoesConfig.ENABLE_ITEM_XP.get() || event.getPlayer() == null) {
            return;
        }
        final ItemStack mainHand = event.getPlayer().getMainHandItem();
        if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.TOOL)) {
            UpgradeService.getInstance().addExperience(mainHand, 1);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        if (!TieredEchoesConfig.ENABLE_ITEM_XP.get()) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            final ItemStack mainHand = player.getMainHandItem();
            if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.WEAPON)) {
                UpgradeService.getInstance().addExperience(mainHand, 5);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (!TieredEchoesConfig.ENABLE_ITEM_XP.get() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (final ItemStack armor : player.getArmorSlots()) {
            if (TierService.getInstance().inferDomains(armor.getItem()).contains(TierDomain.ARMOR)) {
                UpgradeService.getInstance().addExperience(armor, 1);
            }
        }
        final ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (TierService.getInstance().inferDomains(chest.getItem()).contains(TierDomain.ELYTRA)) {
            UpgradeService.getInstance().addExperience(chest, 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (!TieredEchoesConfig.ENABLE_ITEM_XP.get() || event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        tryUpgrade(event.player.getMainHandItem(), event.player);
        tryUpgrade(event.player.getOffhandItem(), event.player);
        for (final ItemStack armor : event.player.getArmorSlots()) {
            tryUpgrade(armor, event.player);
        }
    }

    private static void tryUpgrade(final ItemStack stack, final LivingEntity holder) {
        final var result = UpgradeService.getInstance().tryAutoUpgrade(stack);
        if (result.isPresent()) {
            stack.setTag(result.get().getTag() == null ? null : result.get().getTag().copy());
            PlayerMessageUtil.actionBar(holder, UpgradeService.getInstance().describeStack(stack));
        }
    }
}
