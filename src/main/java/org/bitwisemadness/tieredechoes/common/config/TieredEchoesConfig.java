package org.bitwisemadness.tieredechoes.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TieredEchoesConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_DOMAIN_TABS = BUILDER
            .comment("Whether Tiered Echoes should expose one creative tab per supported domain.")
            .define("creative_tabs.enable_domain_tabs", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_CURIOS_TABS = BUILDER
            .comment("Whether Tiered Echoes should expose dynamic Curios slot tabs when Curios integration is present.")
            .define("creative_tabs.enable_curios_tabs", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_XP = BUILDER
            .comment("Whether the optional item experience system is enabled globally.")
            .define("item_xp.enabled", false);

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_UPGRADE_SINGLE_PATH = BUILDER
            .comment("Whether XP-enabled items should upgrade automatically when only one valid next path exists.")
            .define("item_xp.auto_upgrade_single_path", false);

    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_XP_ONLY_MODE = BUILDER
            .comment("Disables upgrade-recipe-style progression and relies on experience-driven auto-upgrading.")
            .define("item_xp.xp_only_mode", false);

    public static final ForgeConfigSpec.BooleanValue CONSUME_ATTUNEMENT_ITEM = BUILDER
            .comment("Whether shift-right-click attunement consumes the offhand ingredient.")
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
