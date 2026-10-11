package org.bitwisemadness.tieredechoes.common.gamerule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.common.mixin.BooleanValueAccessor;
import org.bitwisemadness.tieredechoes.common.mixin.GameRulesAccessor;
import org.bitwisemadness.tieredechoes.common.network.TENetwork;

/**
 * The mod's three gamerules.
 *
 * <p>Each one exists because the matching behaviour is a pacing choice that belongs to a world
 * rather than to an installation. The config file supplies the default a new world is created
 * with; the gamerule is what an operator reaches for afterwards, and it can be flipped per world
 * without editing a file on disk.</p>
 *
 * <p>Reads must go through {@link #value}, not through a cached field. A rule can change at any
 * time via {@code /gamerule}, so caching would let gameplay drift away from what the command
 * reports.</p>
 */
public final class TEGameRules {

    /** Master switch for item experience. Off leaves smithing progression intact. */
    public static GameRules.Key<GameRules.BooleanValue> ITEM_EXPERIENCE;

    /** Whether an item may advance a tier by accumulating experience. */
    public static GameRules.Key<GameRules.BooleanValue> AUTO_UPGRADE;

    /** Whether an upgrade spends the experience pool, rather than carrying the excess over. */
    public static GameRules.Key<GameRules.BooleanValue> RESET_EXPERIENCE_ON_UPGRADE;

    private TEGameRules() {
    }

    /**
     * Registers the rules. Must run before commands are built, which puts it in common setup.
     *
     * <p>The config value is read here rather than passed in, so that a caller cannot register a
     * rule whose default silently disagrees with the file the world will be created from.</p>
     */
    public static void register() {
        if (ITEM_EXPERIENCE != null) {
            // Re-registering throws "Duplicate gamerule key" from vanilla, which would be an
            // unstartable game rather than a warning. Common setup fires once, so this only
            // guards against a future second call.
            return;
        }
        final boolean itemXpDefault = TieredEchoesConfig.ENABLE_ITEM_XP.get();
        final boolean autoUpgradeDefault = TieredEchoesConfig.ENABLE_AUTO_UPGRADE_SINGLE_PATH.get();
        final boolean resetDefault = TieredEchoesConfig.RESET_EXPERIENCE_ON_UPGRADE.get();

        ITEM_EXPERIENCE = register("teItemExperience", itemXpDefault);
        AUTO_UPGRADE = register("teAutoUpgrade", autoUpgradeDefault);
        RESET_EXPERIENCE_ON_UPGRADE = register("teResetExperienceOnUpgrade", resetDefault);

        TieredEchoes.LOGGER.info("Registered gamerules: teItemExperience={}, teAutoUpgrade={}, "
                        + "teResetExperienceOnUpgrade={} (new worlds start from these)",
                itemXpDefault, autoUpgradeDefault, resetDefault);
    }

    private static GameRules.Key<GameRules.BooleanValue> register(final String name, final boolean configDefault) {
        return GameRulesAccessor.invokeRegister(name, GameRules.Category.MISC,
                BooleanValueAccessor.invokeCreate(configDefault, TEGameRules::onChanged));
    }

    /**
     * Vanilla calls this from {@code Value#changed} whenever the rule is assigned, whether by
     * {@code /gamerule} or by a datapack reload. Connected clients hold a copy of these values for
     * the tooltip, so they have to be told when the copy goes stale.
     */
    private static void onChanged(final MinecraftServer server, final GameRules.BooleanValue changed) {
        TieredEchoes.LOGGER.debug("Gamerule changed, re-syncing progression state to clients");
        TENetwork.syncToAllOnline();
    }

    /**
     * Reads a boolean rule off a running server.
     *
     * <p>The mojmap accessors here are {@code getRule} and {@code getBoolean}; {@code get} is the
     * Yarn name and does not exist in this mapping channel.</p>
     */
    public static boolean itemExperienceEnabled(final MinecraftServer server) {
        return server == null || ITEM_EXPERIENCE == null
                || server.getGameRules().getBoolean(ITEM_EXPERIENCE);
    }

    public static boolean autoUpgradeEnabled(final MinecraftServer server) {
        return server == null || AUTO_UPGRADE == null
                || server.getGameRules().getBoolean(AUTO_UPGRADE);
    }

    public static boolean resetExperienceOnUpgrade(final MinecraftServer server) {
        return server == null || RESET_EXPERIENCE_ON_UPGRADE == null
                || server.getGameRules().getBoolean(RESET_EXPERIENCE_ON_UPGRADE);
    }
}
