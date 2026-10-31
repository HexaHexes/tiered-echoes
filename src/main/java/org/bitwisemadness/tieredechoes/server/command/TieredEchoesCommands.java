package org.bitwisemadness.tieredechoes.server.command;

import com.mojang.brigadier.CommandDispatcher;
import lombok.experimental.UtilityClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReporter;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;
import org.bitwisemadness.tieredechoes.common.upgrade.preview.PreviewUpgradeService;

@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID)
@UtilityClass
public class TieredEchoesCommands {

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tieredechoes")
                .then(Commands.literal("inspect")
                        .then(Commands.literal("hand").executes(ctx -> inspectHand(ctx.getSource()))))
                .then(Commands.literal("validate").executes(ctx -> validate(ctx.getSource())))
                .then(Commands.literal("preview")
                        .then(Commands.argument("domain", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("tier", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .executes(ctx -> preview(
                                                ctx.getSource(),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "domain"),
                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "tier")))))));
    }

    private static int inspectHand(final CommandSourceStack source) {
        if (source.getPlayer() == null) {
            return 0;
        }
        final ItemStack stack = source.getPlayer().getMainHandItem();
        source.sendSuccess(() -> UpgradeService.getInstance().describeStack(stack), false);
        return 1;
    }

    private static int validate(final CommandSourceStack source) {
        ValidationReporter.log(PathService.getInstance().validate(TierService.getInstance()));
        source.sendSuccess(() -> Component.literal("Tiered Echoes validation logged."), false);
        return 1;
    }

    private static int preview(final CommandSourceStack source, final String domainName, final ResourceLocation tierId) {
        if (source.getPlayer() == null) {
            return 0;
        }
        final var domain = org.bitwisemadness.tieredechoes.common.tier.TierDomain.byName(domainName);
        if (domain.isEmpty()) {
            source.sendFailure(Component.literal("Unknown tier domain: " + domainName));
            return 0;
        }
        final ItemStack stack = source.getPlayer().getMainHandItem();
        // Scoped lookup: tier ids repeat across domains, so an unscoped resolve would be ambiguous.
        final var tier = TierService.getInstance().getTier(tierId, domain.get());
        if (tier.isEmpty()) {
            source.sendFailure(Component.literal("Unknown tier: " + tierId + " (domain " + domainName + ")"));
            return 0;
        }
        final ItemStack preview = PreviewUpgradeService.getInstance().createPreviewStack(stack, tier.get());
        source.sendSuccess(() -> Component.literal("Preview: " + preview.getHoverName().getString()), false);
        return 1;
    }
}
