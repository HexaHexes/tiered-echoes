package org.bitwisemadness.tieredechoes.testutil;

import com.mojang.logging.LogUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * One-off Minecraft bootstrap for tests that need real registries ({@code ItemStack}, items, tags).
 *
 * <p>The game does this during startup; a plain JVM test would otherwise see empty registries and
 * fail with errors that read like mod bugs. Everything that can avoid needing this should — value
 * objects, records and codecs all work without it, and those tests stay much faster.</p>
 */
public final class TestBootstrap {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialised;

    private TestBootstrap() {
    }

    public static synchronized void init() {
        if (initialised) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Throwable bootstrapFailure = null;
        try {
            Bootstrap.bootStrap();
        } catch (final Throwable failure) {
            if (!isForgeNetworkInitFailure(failure)) {
                throw failure;
            }
            // Forge's patch ends bootStrap() with NetworkHooks.init(), a class-loading hack that needs
            // FML's listener-list reflection, which a plain JVM cannot satisfy. It runs *after* every
            // vanilla step, so registries are already complete by the time it throws.
            LOGGER.warn("Bootstrap stopped at Forge's NetworkHooks.init(); vanilla registry steps had already completed.");
            bootstrapFailure = failure;
        }
        assertRegistriesReady(bootstrapFailure);
        initialised = true;
    }

    /**
     * Identifies the one bootstrap failure we expect in a test JVM, so that a genuine vanilla
     * bootstrap failure still surfaces instead of being swallowed by a broad catch.
     */
    private static boolean isForgeNetworkInitFailure(final Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            final String className = current.getClass().getName();
            final String message = String.valueOf(current.getMessage());
            if (className.contains("NetworkHooks") || message.contains("NetworkEvent")
                    || message.contains("listener list")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /**
     * A failed bootstrap that leaves registries empty would otherwise surface as an unrelated-looking
     * failure in whichever test touches an item first. Checking the outcome here keeps the error
     * pointing at the harness.
     */
    private static void assertRegistriesReady(final Throwable original) {
        final ResourceLocation diamondSword = BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD);
        if (diamondSword == null || !diamondSword.getPath().equals("diamond_sword")) {
            throw new IllegalStateException("Test bootstrap completed without populating the item registry", original);
        }
    }
}
