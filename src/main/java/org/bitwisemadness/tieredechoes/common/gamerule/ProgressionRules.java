package org.bitwisemadness.tieredechoes.common.gamerule;

import net.minecraft.server.MinecraftServer;

/**
 * The three gamerule values, resolved once and passed down.
 *
 * <p>Exists so that progression can be unit-tested. Reading the rules off a {@code MinecraftServer}
 * is the only correct thing to do in production, but a JUnit test has no server, and threading one
 * through would either force a mock of vanilla or make the rules untestable. Resolving to a plain
 * record at the boundary keeps both honest.</p>
 *
 * @param itemExperience         whether items accumulate experience at all
 * @param autoUpgrade            whether experience alone may advance a tier
 * @param resetExperienceOnUpgrade whether an upgrade spends the pool rather than carrying excess over
 */
public record ProgressionRules(boolean itemExperience, boolean autoUpgrade, boolean resetExperienceOnUpgrade) {

    /** What a world runs with when nothing has been changed: everything on, excess carried over. */
    public static ProgressionRules defaults() {
        return new ProgressionRules(true, true, false);
    }

    public static ProgressionRules from(final MinecraftServer server) {
        return new ProgressionRules(
                TEGameRules.itemExperienceEnabled(server),
                TEGameRules.autoUpgradeEnabled(server),
                TEGameRules.resetExperienceOnUpgrade(server));
    }
}
