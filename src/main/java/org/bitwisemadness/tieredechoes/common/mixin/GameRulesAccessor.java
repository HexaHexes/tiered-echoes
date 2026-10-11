package org.bitwisemadness.tieredechoes.common.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up {@code GameRules.register}, which is the only way a mod can add a gamerule.
 *
 * <p>Mojang made this method private after 1.17, and there is no Forge hook in its place, so the
 * choices are an AccessTransformer or a Mixin accessor. This mod already carries Mixins, so the
 * accessor is the cheaper of the two by one file and no change to {@code build.gradle}.</p>
 *
 * <p>Nothing seals the registry in 1.20.1. There is no freeze flag on the class, so registering
 * during common setup is early enough for command registration and world creation to both see
 * the new keys.</p>
 */
@Mixin(GameRules.class)
public interface GameRulesAccessor {

    @Invoker("register")
    static <T extends GameRules.Value<T>> GameRules.Key<T> invokeRegister(
            final String name,
            final GameRules.Category category,
            final GameRules.Type<T> type) {
        throw new AssertionError("GameRulesAccessor.invokeRegister was not remapped by the annotation processor");
    }
}
