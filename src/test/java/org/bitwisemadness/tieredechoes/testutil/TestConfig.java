package org.bitwisemadness.tieredechoes.testutil;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;

/**
 * Gives tests an in-memory config instead of the file-backed one a real game would load.
 *
 * <p>Forge refuses to answer {@code ConfigValue#get()} until a config object is attached, so any
 * production path that consults an option — xp gating, branch pacing — throws an opaque
 * "config value before config is loaded" error in a test JVM unless this runs first.</p>
 */
public final class TestConfig {

    private static boolean loaded;

    private TestConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        // An empty in-memory config means every option falls back to the default declared in
        // TieredEchoesConfig, which is what a freshly generated config file would contain.
        TieredEchoesConfig.SPEC.setConfig(CommentedConfig.inMemory());
        loaded = true;
    }

    /** Undoes option mutations a test made, so a later test sees the shipped defaults. */
    public static synchronized void resetToDefaults() {
        load();
        TieredEchoesConfig.ENABLE_ITEM_XP.set(true);
        TieredEchoesConfig.ENABLE_AUTO_UPGRADE_SINGLE_PATH.set(true);
        TieredEchoesConfig.CONSUME_ATTUNEMENT_ITEM.set(false);
    }
}
