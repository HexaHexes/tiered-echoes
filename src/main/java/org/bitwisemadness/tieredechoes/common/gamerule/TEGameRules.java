package org.bitwisemadness.tieredechoes.common.gamerule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.mixin.GameRulesAccessor;
import org.bitwisemadness.tieredechoes.common.network.TENetwork;
import org.bitwisemadness.tieredechoes.common.policy.PolicyService;
import org.bitwisemadness.tieredechoes.common.policy.TierPolicy;

/**
 * The mod's five tri-state switches.
 *
 * <p>Each one overrides a {@link TierPolicy} field for a single world. They are integers rather
 * than booleans because the middle state is the useful one: {@code 0} leaves the datapack's answer
 * in place, which is what a world that has never heard of these rules keeps doing. An operator who
 * wants a switch flipped writes {@code 1} to force it off or {@code 2} to force it on, and writes
 * {@code 0} back to hand the decision to the datapack again — something a boolean rule cannot
 * express, because it has no way of saying "stop answering".</p>
 *
 * <p>The three states line up with the two halves of a switch. Forcing a <em>system</em> off makes
 * the mod ignore the state that system wrote to an item; forcing a <em>progression</em> rule off
 * closes the route while leaving what has already been gained intact.</p>
 */
public final class TEGameRules {

    /** Overrides {@code tier_system}: whether tiered items carry a tier at all. */
    public static GameRules.Key<GameRules.IntegerValue> TIERS;

    /** Overrides {@code tier_upgrades}: whether the smithing upgrade route is published. */
    public static GameRules.Key<GameRules.IntegerValue> TIER_UPGRADES;

    /** Overrides {@code levelling_system}: whether items carry a level at all. */
    public static GameRules.Key<GameRules.IntegerValue> LEVELS;

    /** Overrides {@code level_up}: whether an item may gain a level. */
    public static GameRules.Key<GameRules.IntegerValue> LEVEL_UP;

    /** Overrides {@code downgrades}: whether the smithing downgrade route is published. */
    public static GameRules.Key<GameRules.IntegerValue> DOWNGRADES;

    /** Hand the decision back to {@code tier_policies}. This is what a new world starts on. */
    public static final int FOLLOW_DATAPACK = 0;

    /** Force the switch off, whatever the datapack asked for. */
    public static final int FORCE_OFF = 1;

    /** Force the switch on, whatever the datapack asked for. */
    public static final int FORCE_ON = 2;

    private TEGameRules() {
    }

    /**
     * Registers the rules. Must run before commands are built, which puts it in common setup.
     *
     * <p>Every rule defaults to {@link #FOLLOW_DATAPACK}, so a datapack ships a world that behaves
     * exactly as its author wrote it without knowing these rules exist. There is deliberately no
     * config option for a default here: a config default that disagrees with the datapack would be
     * a second source of truth for a question the datapack already answered.</p>
     */
    public static void register() {
        if (TIERS != null) {
            // Re-registering throws "Duplicate gamerule key" from vanilla, which would be an
            // unstartable game rather than a warning. Common setup fires once, so this only
            // guards against a future second call.
            return;
        }
        TIERS = register("teTiers");
        TIER_UPGRADES = register("teTierUpgrades");
        LEVELS = register("teLevels");
        LEVEL_UP = register("teLevelUp");
        DOWNGRADES = register("teDowngrades");

        TieredEchoes.LOGGER.info("Registered gamerules: teTiers, teTierUpgrades, teLevels, teLevelUp, "
                + "teDowngrades. Each is 0 = follow the datapack (default), 1 = force off, 2 = force on.");
    }

    private static GameRules.Key<GameRules.IntegerValue> register(final String name) {
        return GameRulesAccessor.invokeRegister(name, GameRules.Category.MISC,
                GameRules.IntegerValue.create(FOLLOW_DATAPACK, TEGameRules::onChanged));
    }

    /**
     * Vanilla calls this from {@code Value#changed} whenever the rule is assigned, whether by
     * {@code /gamerule} or by a datapack reload. The resolved switches are read from gameplay on
     * both sides, so the copy connected clients hold has to be refreshed the moment a rule moves.
     */
    private static void onChanged(final MinecraftServer server, final GameRules.IntegerValue changed) {
        TieredEchoes.LOGGER.debug("Gamerule changed, re-resolving switches and re-syncing clients");
        PolicyService.getInstance().applyOverrides(server);
        TENetwork.syncToAllOnline();
    }

    /**
     * Applies this world's overrides on top of a declared policy.
     *
     * <p>A {@code null} server is not an error — it is a world that does not exist yet, or a client
     * with no business resolving anything — and leaves the declared answer standing. A rule key
     * that was never registered reads the same way, which matters because registration happens in
     * common setup and something may ask before it has run.</p>
     */
    public static TierPolicy applyOverrides(final MinecraftServer server, final TierPolicy declared) {
        if (server == null) {
            return declared;
        }
        final GameRules rules = server.getGameRules();
        return new TierPolicy(
                resolve(rules.getInt(TIERS), declared.tierSystem()),
                resolve(rules.getInt(TIER_UPGRADES), declared.tierUpgrades()),
                resolve(rules.getInt(LEVELS), declared.levellingSystem()),
                resolve(rules.getInt(LEVEL_UP), declared.levelUp()),
                resolve(rules.getInt(DOWNGRADES), declared.downgrades()));
    }

    /**
     * One rule against the value a datapack asked for.
     *
     * <p>Deliberately free of Minecraft types so the three states can be reasoned about directly.
     * Anything that is not one of the two forced states means "follow the datapack", so a pack that
     * writes -1 or 3 gets the documented default rather than a silently inverted world.</p>
     */
    static boolean resolve(final int ruleValue, final boolean declared) {
        return switch (ruleValue) {
            case FORCE_OFF -> false;
            case FORCE_ON -> true;
            default -> declared;
        };
    }
}
