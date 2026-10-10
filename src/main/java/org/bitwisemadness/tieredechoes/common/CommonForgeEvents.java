package org.bitwisemadness.tieredechoes.common;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
public final class CommonForgeEvents {
    private static final String TIERED_ECHOES_MARKER = " [Tiered Echoes]";

    private CommonForgeEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAttributeModifiers(final ItemAttributeModifierEvent event) {
        final ItemStack stack = event.getItemStack();
        final var tierOpt = UpgradeService.getInstance().getCurrentTier(stack);
        if (tierOpt.isEmpty()) return;
        final TierDefinition tier = tierOpt.get();
        final Set<Attribute> presentAttributes = new LinkedHashSet<>(event.getModifiers().keySet());
        int scaled = 0;
        for (final Attribute attribute : presentAttributes) {
            final java.util.Optional<Double> multiplier = TierService.getInstance().getAttributeMultiplier(tier, attribute);
            if (multiplier.isPresent()) {
                scaled += applyScaledItemModifier(event, tier, attribute, multiplier.get());
            } else {
                logUnrecognizedModdedAttribute(stack, attribute);
            }
        }
        TieredEchoes.LOGGER.debug("[CommonForgeEvents] Item attribute event processed for {} tier {} slot {} presentAttributes={} scaled={}", stack.getItem(), tier.id(), event.getSlotType(), presentAttributes, scaled);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotentialCurioAttributeModifiers(final Event event) {
        if (!event.getClass().getName().equals("top.theillusivec4.curios.api.event.CurioAttributeModifierEvent")) {
            return;
        }
        final ItemStack stack = extractCurioStack(event);
        if (stack.isEmpty()) {
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] CurioAttributeModifierEvent seen, but no ItemStack accessor was found on {}", event.getClass().getName());
            return;
        }
        final var tierOpt = UpgradeService.getInstance().getCurrentTier(stack);
        if (tierOpt.isEmpty()) {
            return;
        }
        final Object modifiers = invokeNoArg(event, "getModifiers");
        if (!(modifiers instanceof Multimap<?, ?> rawMap)) {
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] CurioAttributeModifierEvent for {} did not expose a Guava Multimap through getModifiers()", stack.getItem());
            return;
        }
        @SuppressWarnings("unchecked")
        final Multimap<Attribute, AttributeModifier> map = (Multimap<Attribute, AttributeModifier>) rawMap;
        final int scaled = applyScaledMultimapModifiers(event, stack, tierOpt.get(), map, "Curios");
        TieredEchoes.LOGGER.debug("[CommonForgeEvents] Curios attribute event processed for {} tier {} scaled={}", stack.getItem(), tierOpt.get().id(), scaled);
    }

    private static int applyScaledItemModifier(final ItemAttributeModifierEvent event, final TierDefinition tier, final Attribute attribute, final double multiplier) {
        if (multiplier == 1.0D) {
            return 0;
        }
        int scaled = 0;
        final List<AttributeModifier> existing = List.copyOf(event.getModifiers().get(attribute));
        for (final AttributeModifier modifier : existing) {
            final AttributeModifier replacement = scaledReplacement(modifier, multiplier);
            if (replacement == null) {
                continue;
            }
            event.removeModifier(attribute, modifier);
            event.addModifier(attribute, replacement);
            scaled++;
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] Scaled item attribute {} from {} to {} via {}", attribute.getDescriptionId(), modifier.getAmount(), replacement.getAmount(), tier.id());
        }
        return scaled;
    }

    private static int applyScaledMultimapModifiers(final Event event, final ItemStack stack, final TierDefinition tier, final Multimap<Attribute, AttributeModifier> modifiers, final String source) {
        int scaled = 0;
        final Set<Attribute> presentAttributes = new LinkedHashSet<>(modifiers.keySet());
        for (final Attribute attribute : presentAttributes) {
            final java.util.Optional<Double> multiplierOpt = TierService.getInstance().getAttributeMultiplier(tier, attribute);
            if (multiplierOpt.isEmpty()) {
                logUnrecognizedModdedAttribute(stack, attribute);
                continue;
            }
            final double multiplier = multiplierOpt.get();
            if (multiplier == 1.0D) {
                continue;
            }
            final List<AttributeModifier> existing = List.copyOf(modifiers.get(attribute));
            for (final AttributeModifier modifier : existing) {
                final AttributeModifier extra = additiveScaledExtra(modifier, multiplier, tier, attribute.getDescriptionId(), source);
                if (extra == null) {
                    continue;
                }
                if (addCurioModifier(event, attribute, extra)) {
                    scaled++;
                    TieredEchoes.LOGGER.debug("[CommonForgeEvents] Added {} scaling modifier for {} base {} extra {} via {}", source, attribute.getDescriptionId(), modifier.getAmount(), extra.getAmount(), tier.id());
                } else {
                    TieredEchoes.LOGGER.warn("[CommonForgeEvents] Could not add {} scaling modifier for {} on {} because {} exposes an unmodifiable modifier map and no supported addModifier API was found", source, attribute.getDescriptionId(), stack.getItem(), event.getClass().getName());
                }
            }
        }
        return scaled;
    }


    private static AttributeModifier additiveScaledExtra(final AttributeModifier modifier, final double multiplier, final TierDefinition tier, final String key, final String source) {
        if (modifier.getName().contains(TIERED_ECHOES_MARKER)) {
            return null;
        }
        final double extraAmount = modifier.getAmount() * (multiplier - 1.0D);
        if (Math.abs(extraAmount) < 1.0E-9D) {
            return null;
        }
        final UUID id = UUID.nameUUIDFromBytes(("tieredechoes:" + source + ':' + tier.id() + ':' + key + ':' + modifier.getId()).getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(id, modifier.getName() + TIERED_ECHOES_MARKER, extraAmount, modifier.getOperation());
    }

    private static boolean addCurioModifier(final Event event, final Attribute attribute, final AttributeModifier modifier) {
        try {
            final Method method = event.getClass().getMethod("addModifier", Attribute.class, AttributeModifier.class);
            method.invoke(event, attribute, modifier);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static AttributeModifier scaledReplacement(final AttributeModifier modifier, final double multiplier) {
        if (modifier.getName().contains(TIERED_ECHOES_MARKER)) {
            return null;
        }
        final double scaledAmount = modifier.getAmount() * multiplier;
        if (Double.compare(scaledAmount, modifier.getAmount()) == 0) {
            return null;
        }
        return new AttributeModifier(modifier.getId(), modifier.getName() + TIERED_ECHOES_MARKER, scaledAmount, modifier.getOperation());
    }

    private static ItemStack extractCurioStack(final Event event) {
        final Object itemStack = invokeNoArg(event, "getItemStack");
        if (itemStack instanceof ItemStack stack) {
            return stack;
        }
        final Object stack = invokeNoArg(event, "getStack");
        if (stack instanceof ItemStack itemStackFallback) {
            return itemStackFallback;
        }
        return ItemStack.EMPTY;
    }

    private static Object invokeNoArg(final Object target, final String methodName) {
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void logUnrecognizedModdedAttribute(final ItemStack stack, final Attribute attribute) {
        final var attrId = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (attrId != null && (attrId.getNamespace().contains("ars") || attrId.getNamespace().contains("legendary") || attrId.getNamespace().contains("lso") || attrId.getNamespace().contains("survival"))) {
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] Saw unrecognized modded attribute {} ({}) on {}", attrId, attribute.getDescriptionId(), stack.getItem());
        }
    }

    @Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID, value = Dist.CLIENT)
    public static final class ClientTooltips {
        private ClientTooltips() {}

        @SubscribeEvent
        public static void onItemTooltip(final ItemTooltipEvent event) {
            final ItemStack stack = event.getItemStack();
            final var tierOpt = UpgradeService.getInstance().getCurrentTier(stack);
            if (tierOpt.isPresent()) {
                TieredEchoes.LOGGER.debug("[CommonForgeEvents] Tiered stack tooltip observed for {} tier {}", stack.getItem(), tierOpt.get().id());
            }
        }
    }
}
