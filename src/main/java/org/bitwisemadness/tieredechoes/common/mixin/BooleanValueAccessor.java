package org.bitwisemadness.tieredechoes.common.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BiConsumer;

/**
 * Opens up the two-argument {@code GameRules.BooleanValue.create}.
 *
 * <p>The single-argument overload would be enough to register a rule, but it is package-private
 * like its sibling here. The two-argument form is wanted for its second parameter: vanilla calls
 * that {@code BiConsumer} from {@code Value#changed} whenever the rule is assigned, which is how
 * this mod pushes a re-sync to connected players after an operator runs {@code /gamerule}.</p>
 *
 * <p>Without that callback the rule would still govern gameplay, but a client that was already
 * connected would keep rendering against the old value until the next datapack reload.</p>
 */
@Mixin(GameRules.BooleanValue.class)
public interface BooleanValueAccessor {

    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> invokeCreate(
            final boolean defaultValue,
            final BiConsumer<MinecraftServer, GameRules.BooleanValue> changedAction) {
        throw new AssertionError("BooleanValueAccessor.invokeCreate was not remapped by the annotation processor");
    }
}
