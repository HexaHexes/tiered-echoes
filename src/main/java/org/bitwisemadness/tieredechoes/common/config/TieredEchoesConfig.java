package org.bitwisemadness.tieredechoes.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TieredEchoesConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_XP = BUILDER
            .comment("Whether the optional item experience system is enabled globally.",
                    "This is the operator's master switch; which routes progression takes is decided by tier_policies.")
            .define("item_xp.enabled", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_UPGRADE_SINGLE_PATH = BUILDER
            .comment("Whether XP-enabled items should upgrade automatically when only one valid next path exists.",
                    "Kept as a config because it is a pacing choice an operator may want to tune without a datapack.")
            .define("item_xp.auto_upgrade_single_path", true);

    public static final ForgeConfigSpec.BooleanValue CONSUME_ATTUNEMENT_ITEM = BUILDER
            .comment("Whether shift-right-click attunement consumes the offhand ingredient.",
                    "A tier_policies file that states consume_attunement_item takes precedence over this.")
            .define("item_xp.consume_attunement_item", false);

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
