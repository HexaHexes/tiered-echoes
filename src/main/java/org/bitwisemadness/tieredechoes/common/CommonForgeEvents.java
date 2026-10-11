package org.bitwisemadness.tieredechoes.common;

import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import org.bitwisemadness.tieredechoes.common.experience.ExperienceTooltip;
import org.bitwisemadness.tieredechoes.common.network.client.DisplayEdge;
import org.bitwisemadness.tieredechoes.common.network.client.SyncedProgressionState;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
public final class CommonForgeEvents {
    private static final String TIERED_ECHOES_MARKER = " [Tiered Echoes]";

    private CommonForgeEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAttributeModifiers(final ItemAttributeModifierEvent event) {
        final ItemStack stack = event.getItemStack();
        final Set<Attribute> presentAttributes = new LinkedHashSet<>(event.getModifiers().keySet());
        int scaled = 0;
        for (final Attribute attribute : presentAttributes) {
            // The multiplier is resolved across every ladder the item climbs, so an axe's weapon rung
            // and tool rung both get to state a modifier for the attributes they own.
            final java.util.Optional<Double> multiplier = TierService.getInstance().getAttributeMultiplier(stack, attribute);
            if (multiplier.isPresent()) {
                scaled += applyScaledItemModifier(event, stack, attribute, multiplier.get());
            } else {
                logUnrecognizedModdedAttribute(stack, attribute);
            }
        }
        TieredEchoes.LOGGER.debug("[CommonForgeEvents] Item attribute event processed for {} slot {} presentAttributes={} scaled={}", stack.getItem(), event.getSlotType(), presentAttributes, scaled);
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
        final Object modifiers = invokeNoArg(event, "getModifiers");
        if (!(modifiers instanceof Multimap<?, ?> rawMap)) {
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] CurioAttributeModifierEvent for {} did not expose a Guava Multimap through getModifiers()", stack.getItem());
            return;
        }
        @SuppressWarnings("unchecked")
        final Multimap<Attribute, AttributeModifier> map = (Multimap<Attribute, AttributeModifier>) rawMap;
        final int scaled = applyScaledMultimapModifiers(event, stack, map, "Curios");
        TieredEchoes.LOGGER.debug("[CommonForgeEvents] Curios attribute event processed for {} scaled={}", stack.getItem(), scaled);
    }

    private static int applyScaledItemModifier(final ItemAttributeModifierEvent event, final ItemStack stack, final Attribute attribute, final double multiplier) {
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
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] Scaled item attribute {} from {} to {} via x{}", attribute.getDescriptionId(), modifier.getAmount(), replacement.getAmount(), multiplier);
        }
        return scaled;
    }

    private static int applyScaledMultimapModifiers(final Event event, final ItemStack stack, final Multimap<Attribute, AttributeModifier> modifiers, final String source) {
        int scaled = 0;
        final Set<Attribute> presentAttributes = new LinkedHashSet<>(modifiers.keySet());
        for (final Attribute attribute : presentAttributes) {
            final java.util.Optional<Double> multiplierOpt = TierService.getInstance().getAttributeMultiplier(stack, attribute);
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
                final AttributeModifier extra = additiveScaledExtra(modifier, multiplier, attribute.getDescriptionId(), source);
                if (extra == null) {
                    continue;
                }
                if (addCurioModifier(event, attribute, extra)) {
                    scaled++;
                    TieredEchoes.LOGGER.debug("[CommonForgeEvents] Added {} scaling modifier for {} base {} extra {} via x{}", source, attribute.getDescriptionId(), modifier.getAmount(), extra.getAmount(), multiplier);
                } else {
                    TieredEchoes.LOGGER.warn("[CommonForgeEvents] Could not add {} scaling modifier for {} on {} because {} exposes an unmodifiable modifier map and no supported addModifier API was found", source, attribute.getDescriptionId(), stack.getItem(), event.getClass().getName());
                }
            }
        }
        return scaled;
    }


    private static AttributeModifier additiveScaledExtra(final AttributeModifier modifier, final double multiplier, final String key, final String source) {
        if (modifier.getName().contains(TIERED_ECHOES_MARKER)) {
            return null;
        }
        final double extraAmount = modifier.getAmount() * (multiplier - 1.0D);
        if (Math.abs(extraAmount) < 1.0E-9D) {
            return null;
        }
        // The seed includes the multiplier rather than a tier id, because the multiplier is now
        // resolved across every ladder the item climbs and no single tier owns it. Two different
        // scale factors still produce two different ids, so a retiered item replaces rather than
        // accumulates its scaling modifier.
        final UUID id = UUID.nameUUIDFromBytes(("tieredechoes:" + source + ':' + multiplier + ':' + key + ':' + modifier.getId()).getBytes(StandardCharsets.UTF_8));
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
            final UpgradeService upgrade = UpgradeService.getInstance();
            final List<TierDomain> ladders = upgrade.ladderDomains(stack);
            if (ladders.isEmpty()) {
                return;
            }
            TieredEchoes.LOGGER.debug("[CommonForgeEvents] Tiered stack tooltip observed for {} ladders {}",
                    stack.getItem(), ladders);

            // The gamerule is per-world and vanilla gives a client no way to read it, so the value
            // shown here arrived over the mod's own sync channel. See SyncedProgressionState.
            if (!SyncedProgressionState.itemExperienceEnabled()) {
                return;
            }

            // An axe climbs both the weapon and the tool ladder, so it has two pools and two next
            // thresholds. Each gets its own line: collapsing them to one would show a single number
            // that belongs to neither ladder. A ladder the item has earned experience on but not yet
            // been tiered still gets a line, because that experience is the progress it is sitting on.
            for (final TierDomain domain : ladders) {
                final TierDefinition tier = upgrade.getCurrentTier(stack, domain).orElse(null);
                if (tier == null) {
                    continue;
                }
                final List<Integer> costs = new ArrayList<>(2);
                for (final DisplayEdge edge : SyncedProgressionState.nextEdges(tier.id(), domain)) {
                    final Optional<Integer> override = edge.experienceRequirementOverride();
                    if (override.isPresent()) {
                        costs.add(override.get());
                        continue;
                    }
                    // A target the datapack no longer defines is skipped rather than treated as free.
                    TierService.getInstance().getTier(edge.to(), edge.domain())
                            .map(TierDefinition::experienceRequired)
                            .ifPresent(costs::add);
                }
                ExperienceTooltip.nextThreshold(costs).ifPresent(threshold -> event.getToolTip().add(
                        Component.literal(ExperienceTooltip.format(upgrade.getStoredExperience(stack, domain), threshold))
                                .withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
    }
}
