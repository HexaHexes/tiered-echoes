package org.bitwisemadness.tieredechoes.common.level;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.util.PlayerMessageUtil;

import java.util.OptionalInt;

/**
 * Feeds the items a player actually uses.
 *
 * <p>Every award names the stack the action involved — the sword that landed the hit, the pickaxe
 * that broke the block, the armour piece that took the blow — because a level belongs to the item
 * rather than to a ladder. An axe therefore earns from both mining and kills into one pool, which
 * is exactly right: it is one axe.</p>
 *
 * <p>The broad award sites are deliberate. Eligibility is {@code LevelService#canEarn}'s answer, so
 * handing a stack that cannot earn costs one tag lookup and nothing else — which is what lets a
 * datapack declare a shield or elytra ladder and have its gear start earning with no Java change
 * waiting behind it.</p>
 */
@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
public final class LevelExperienceHandler {

    /** A kill is worth a handful of uses, the same weight the old ladder gave it. */
    private static final int KILL_EXPERIENCE = 5;

    private LevelExperienceHandler() {
    }

    @SubscribeEvent
    public static void onAttackEntity(final AttackEntityEvent event) {
        award(event.getEntity(), event.getEntity().getMainHandItem(), 1);
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        award(event.getPlayer(), event.getPlayer().getMainHandItem(), 1);
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            award(player, player.getMainHandItem(), KILL_EXPERIENCE);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (final ItemStack armor : player.getArmorSlots()) {
            award(player, armor, 1);
        }
        // getArmorSlots() covers only the four armour slots, so the chest slot's non-armour
        // occupant — an elytra — and the off-hand are read separately. Both are inert until a
        // datapack puts a rung on one, at which point the ladder they joined needs experience to
        // climb and this is where it comes from.
        award(player, player.getItemBySlot(EquipmentSlot.CHEST), 1);
        award(player, player.getOffhandItem(), 1);
    }

    /**
     * Banks experience on one stack and announces the level it bought, if any.
     *
     * <p>The gate lives in {@code canEarn}, so this method's own test is only whether there was
     * anything to award — an empty stack or a non-positive amount is a caller mistake rather than
     * a gameplay state. The message goes to the holder rather than the stack because the action bar
     * belongs to a player: the item levels up, but the player is the one waiting to hear about it.</p>
     */
    private static void award(final LivingEntity holder, final ItemStack stack, final int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        final OptionalInt leveled = LevelService.getInstance().addExperience(stack, amount);
        if (leveled.isPresent()) {
            PlayerMessageUtil.actionBar(holder, Component.literal(
                            stack.getHoverName().getString() + " reached level " + leveled.getAsInt())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
