package org.bitwisemadness.tieredechoes.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The installation's options.
 *
 * <p>Progression does not live here. Whether a world has tiers, whether they may be upgraded and
 * whether an item may level are all questions the datapack already answers in {@code
 * tier_policies}, and a config default that disagreed with the pack would be a second source of
 * truth for a question with a first. An operator who wants one of those flipped reaches for the
 * matching {@code te*} gamerule, which is scoped to a single world and reversible.</p>
 */
public final class TieredEchoesConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue WRITE_VALIDATION_JSON = BUILDER
            .comment("Whether validation should write a JSON report to the game directory.")
            .define("debug.write_validation_json", true);

    public static final ForgeConfigSpec.BooleanValue COMPAT_AUDIT_MODE = BUILDER
            .comment("Logs detected compat capabilities and bridge activation details for debugging.")
            .define("debug.compat_audit_mode", false);

    public static final ForgeConfigSpec.BooleanValue COMPAT_AUDIT_VERBOSE = BUILDER
            .comment("Adds more verbose compat audit logging when compat_audit_mode is enabled.")
            .define("debug.compat_audit_verbose", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private TieredEchoesConfig() {
    }
}
