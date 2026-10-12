package org.bitwisemadness.tieredechoes.testutil;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;

/**
 * Gives tests an in-memory config instead of the file-backed one a real game would load.
 *
 * <p>Forge refuses to answer {@code ConfigValue#get()} until a config object is attached, so any
 * production path that consults an option — writing a validation report, the compat audit — throws
 * an opaque "config value before config is loaded" error in a test JVM unless this runs first.</p>
 *
 * <p>Only the debug options are left to reset, and nothing currently mutates them: the progression
 * switches used to live here too, but they are questions the datapack answers now and a config
 * default that disagreed with the pack would be a second source of truth for a question with a
 * first.</p>
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
}
