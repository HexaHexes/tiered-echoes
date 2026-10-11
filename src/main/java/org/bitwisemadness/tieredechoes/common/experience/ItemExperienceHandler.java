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

/**
 * Awards experience to the ladder the action actually fed.
 *
 * <p>Every award names a domain, because an axe in both the weapon and tool tags climbs both: kills
 * feed its weapon ladder, mining feeds its tool ladder, and neither may advance the other. The
 * membership test is {@code contains(domain)} rather than a single inferred domain so that a shield
 * blocking in the off-hand credits the shield ladder even when the same item also sits in another
 * tag.</p>
 */
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
        award(mainHand, TierDomain.WEAPON, 1);
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || !itemExperienceEnabled(event.getPlayer())) {
            return;
        }
        award(event.getPlayer().getMainHandItem(), TierDomain.TOOL, 1);
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && itemExperienceEnabled(player)) {
            award(player.getMainHandItem(), TierDomain.WEAPON, 5);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !itemExperienceEnabled(player)) {
            return;
        }
        for (final ItemStack armor : player.getArmorSlots()) {
            award(armor, TierDomain.ARMOR, 1);
        }
        // The three awards below look like leftovers now that shields, elytras and ranged weapons
        // ship no ladder, and they are deliberately kept. award() checks domain membership, so each
        // one is inert until a datapack declares a ladder for its domain — at which point the ladder
        // needs experience to climb and these are where it comes from. Removing them would make
        // declaring a shield ladder a change that requires a Java patch, which is the one thing the
        // tag-driven membership exists to avoid.
        //
        // getArmorSlots() covers only the four armour slots, so an elytra — which occupies the chest
        // slot without being armour — is read separately.
        award(player.getItemBySlot(EquipmentSlot.CHEST), TierDomain.ELYTRA, 1);
        // The off-hand is read separately for the domains that actually live there rather than
        // looping every held item, so a weapon in the off-hand does not earn twice — once here and
        // once from the weapon path.
        final ItemStack offhand = player.getOffhandItem();
        award(offhand, TierDomain.SHIELD, 1);
        award(offhand, TierDomain.RANGED_WEAPONS, 1);
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

    /**
     * Feeds one ladder, if the item belongs to it. Membership is checked here so the call sites stay
     * a plain statement of which action feeds which ladder.
     */
    private static void award(final ItemStack stack, final TierDomain domain, final int amount) {
        if (stack.isEmpty()) {
            return;
        }
        if (!TierService.getInstance().inferDomains(stack.getItem()).contains(domain)) {
            return;
        }
        UpgradeService.getInstance().addExperience(stack, domain, amount);
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
