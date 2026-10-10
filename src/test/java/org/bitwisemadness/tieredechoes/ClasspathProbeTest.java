package org.bitwisemadness.tieredechoes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the test harness itself rather than any mod behaviour: ForgeGradle does not obviously put
 * the Minecraft jars on the test classpath, and if it ever stops doing so, every other test in this
 * suite fails with a confusing NoClassDefFoundError instead of one clear signal here.
 */
class ClasspathProbeTest {

    @Test
    void minecraftClassesResolveOnTestClasspath() {
        assertEquals("minecraft:diamond", new ResourceLocation("minecraft", "diamond").toString());
        assertEquals(Rarity.EPIC, Rarity.valueOf("EPIC"));
    }

    @Test
    void nettyBufferResolvesForCodecTests() {
        assertEquals(0, io.netty.buffer.Unpooled.buffer().capacity() - io.netty.buffer.Unpooled.buffer().capacity());
    }
}
