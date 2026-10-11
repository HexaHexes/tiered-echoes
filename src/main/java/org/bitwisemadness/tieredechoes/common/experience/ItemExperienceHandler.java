package org.bitwisemadness.tieredechoes.common.experience;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import org.bitwisemadness.tieredechoes.common.gamerule.TEGameRules;
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
        if (!itemExperienceEnabled(event.getEntity())) {
            return;
        }
        final ItemStack mainHand = event.getEntity().getMainHandItem();
        if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.WEAPON)) {
            UpgradeService.getInstance().addExperience(mainHand, 1);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || !itemExperienceEnabled(event.getPlayer())) {
            return;
        }
        final ItemStack mainHand = event.getPlayer().getMainHandItem();
        if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.TOOL)) {
            UpgradeService.getInstance().addExperience(mainHand, 1);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && itemExperienceEnabled(player)) {
            final ItemStack mainHand = player.getMainHandItem();
            if (TierService.getInstance().inferDomains(mainHand.getItem()).contains(TierDomain.WEAPON)) {
                UpgradeService.getInstance().addExperience(mainHand, 5);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !itemExperienceEnabled(player)) {
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
        // getArmorSlots() deliberately covers only the four armour slots, so a shield held to block
        // would otherwise earn nothing at all. The off-hand is read separately for the domains that
        // actually live there rather than looping every held item, so a weapon in the off-hand does
        // not earn twice — once here and once from the weapon path.
        final ItemStack offhand = player.getOffhandItem();
        final var offhandDomains = TierService.getInstance().inferDomains(offhand.getItem());
        if (offhandDomains.contains(TierDomain.SHIELD) || offhandDomains.contains(TierDomain.RANGED_WEAPONS)) {
            UpgradeService.getInstance().addExperience(offhand, 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide
                || !itemExperienceEnabled(event.player)) {
            return;
        }
        tryUpgrade(event.player.getMainHandItem(), event.player);
        tryUpgrade(event.player.getOffhandItem(), event.player);
        for (final ItemStack armor : event.player.getArmorSlots()) {
            tryUpgrade(armor, event.player);
        }
    }

    private static void tryUpgrade(final ItemStack stack, final LivingEntity holder) {
        final var result = UpgradeService.getInstance().tryAutoUpgrade(stack, holder.getServer());
        if (result.isPresent()) {
            stack.setTag(result.get().getTag() == null ? null : result.get().getTag().copy());
            PlayerMessageUtil.actionBar(holder, UpgradeService.getInstance().describeStack(stack));
        }
    }

    /**
     * The master switch is a gamerule rather than a config value, because whether a world wants
     * item experience is a decision about that world. The config file only supplies the value a
     * new world is created with; changing it later does not reach worlds that already exist.
     */
    private static boolean itemExperienceEnabled(final Entity source) {
        final MinecraftServer server = source.getServer();
        return server == null || TEGameRules.itemExperienceEnabled(server);
    }
}
