package org.bitwisemadness.tieredechoes.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterTextureAtlasSpriteLoadersEvent;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.client.textures.ITextureAtlasSpriteLoader;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.rarity.TERarities;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Builds every upgrade-template texture from the vanilla netherite upgrade template at load time.
 *
 * <p>The mod ships no template art of its own. Each texture is a stub PNG whose {@code .mcmeta}
 * names this loader, and the loader discards the stub in favour of
 * {@code minecraft:textures/item/netherite_upgrade_smithing_template.png}, read through the resource
 * manager and hue-shifted to the tier's colour. Reading through the manager rather than baking a
 * copy into the jar is the whole point: a resource pack that restyles the vanilla template restyles
 * ours with it, on the next reload, with no action from anyone.</p>
 *
 * <p>The shift rotates hue rather than replacing it. The vanilla template is two-tone — a dark
 * maroon plate with a cyan arrow — and flattening that to one colour would throw away the contrast
 * that makes it read as a template at all. Rotation carries both tones together, so every tier keeps
 * the original's shading and its plate-versus-arrow separation while gaining a colour of its own.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TieredEchoes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TemplateTintSpriteLoader implements ITextureAtlasSpriteLoader {

    /** The name the {@code .mcmeta} files use. Forge prefixes it with this mod's namespace. */
    public static final String NAME = "template_tint";

    private static final ResourceLocation SOURCE_TEXTURE =
            new ResourceLocation("minecraft", "textures/item/netherite_upgrade_smithing_template.png");

    /**
     * Hue rotation and saturation scale per tier, in ladder order.
     *
     * <p>The rotations were chosen so the resulting plate colour lands on the tier's own rarity —
     * legendary rotates onto golden orange, matching {@link TERarities#LEGENDARY_COLOR}. Saturation
     * is scaled rather than pinned so that a resource pack supplying a flatter or richer source
     * still gets a flatter or richer result instead of an identical one.</p>
     */
    private static final Map<String, Tint> TINTS = Map.of(
            "common", new Tint(200.0F, 0.55F),
            "uncommon", new Tint(105.0F, 1.10F),
            "rare", new Tint(195.0F, 1.15F),
            "epic", new Tint(275.0F, 1.15F),
            "legendary", new Tint(32.0F, 1.25F));

    @SubscribeEvent
    public static void registerLoaders(final RegisterTextureAtlasSpriteLoadersEvent event) {
        event.register(NAME, new TemplateTintSpriteLoader());
    }

    @Override
    public SpriteContents loadContents(final ResourceLocation name, final Resource resource,
                                       final FrameSize frameSize, final NativeImage image,
                                       final AnimationMetadataSection animationMeta,
                                       final ForgeTextureMetadata forgeMeta) {
        final Tint tint = TINTS.get(tierOf(name));
        if (tint == null) {
            // A texture this loader does not own. Declaring the loader in its .mcmeta was a
            // mistake, but a mistake that should cost the texture its tint, not the game its boot.
            return new SpriteContents(name, frameSize, image, animationMeta, forgeMeta);
        }
        final NativeImage source = readSource(image);
        if (source == image) {
            // Fell back to the stub, so the stub is what the atlas takes ownership of.
            return new SpriteContents(name, frameSize, image, animationMeta, forgeMeta);
        }
        // The stub is in no returned contents, and nothing above us closes it either:
        // SpriteLoader#loadSprite drops its reference on the non-null path and hands the image's
        // lifetime to whatever the loader returned. This close is therefore the only one it gets.
        // Idempotent, so a Forge that starts closing it too would not double-free.
        image.close();
        shiftHue(source, tint);
        // Ownership passes to the SpriteContents, which closes what it holds when the atlas is
        // rebuilt. Closing here would hand the atlas a freed image.
        return new SpriteContents(name, new FrameSize(source.getWidth(), source.getHeight()),
                source, AnimationMetadataSection.EMPTY, forgeMeta);
    }

    @Override
    public TextureAtlasSprite makeSprite(final ResourceLocation atlasName, final SpriteContents contents,
                                         final int atlasWidth, final int atlasHeight,
                                         final int spriteX, final int spriteY, final int mipmapLevel) {
        return new TemplateSprite(atlasName, contents, spriteX, spriteY, atlasWidth, atlasHeight);
    }

    /**
     * The vanilla template, or the stub when it cannot be read.
     *
     * <p>The stub is returned by reference rather than copied, so the caller can tell a real read
     * from a bail-out by identity — and must leave it alone when it does.</p>
     *
     * <p>Failing to a visible stub beats failing to nothing: the alternative is an invisible item in
     * a player's inventory and a stack trace in the log, and the difference between those two
     * outcomes is one fallback.</p>
     */
    private static NativeImage readSource(final NativeImage fallback) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return fallback;
        }
        try {
            final Resource resource = minecraft.getResourceManager().getResource(SOURCE_TEXTURE).orElse(null);
            if (resource == null) {
                TieredEchoes.LOGGER.warn("[TieredEchoes] {} is missing; template textures fall back to their stub art",
                        SOURCE_TEXTURE);
                return fallback;
            }
            try (InputStream stream = resource.open()) {
                return NativeImage.read(stream);
            }
        } catch (final IOException | RuntimeException exception) {
            TieredEchoes.LOGGER.warn("[TieredEchoes] Could not read {}; template textures fall back to their stub art",
                    SOURCE_TEXTURE, exception);
            return fallback;
        }
    }

    /**
     * Rotates hue and scales saturation in place, leaving lightness and alpha untouched.
     *
     * <p>{@code NativeImage#getPixelRGBA} returns its int in ABGR order — the name describes the
     * image's byte layout, not the packing of the value — so red sits in the low byte and alpha in
     * the high one. Every fully transparent pixel is skipped, because rotating the hue of nothing
     * costs a conversion and can only produce a wrong answer.</p>
     */
    private static void shiftHue(final NativeImage image, final Tint tint) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getPixelRGBA(x, y);
                final int alpha = (pixel >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                final int red = pixel & 0xFF;
                final int green = (pixel >>> 8) & 0xFF;
                final int blue = (pixel >>> 16) & 0xFF;

                final float[] hsl = rgbToHsl(red, green, blue);
                hsl[0] = (hsl[0] + tint.hueShift()) % 360.0F;
                hsl[1] = Math.min(1.0F, hsl[1] * tint.saturationScale());

                final int[] rgb = hslToRgb(hsl[0], hsl[1], hsl[2]);
                image.setPixelRGBA(x, y, (alpha << 24) | (rgb[2] << 16) | (rgb[1] << 8) | rgb[0]);
            }
        }
    }

    /** {@code item/legendary_upgrade_template} to {@code legendary}. */
    private static String tierOf(final ResourceLocation name) {
        final String path = name.getPath();
        final int slash = path.lastIndexOf('/');
        final String file = slash < 0 ? path : path.substring(slash + 1);
        final int underscore = file.indexOf('_');
        return underscore < 0 ? file : file.substring(0, underscore);
    }

    private static float[] rgbToHsl(final int red, final int green, final int blue) {
        final float r = red / 255.0F;
        final float g = green / 255.0F;
        final float b = blue / 255.0F;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float lightness = (max + min) / 2.0F;
        if (max == min) {
            return new float[]{0.0F, 0.0F, lightness};
        }
        final float difference = max - min;
        final float saturation = lightness > 0.5F
                ? difference / (2.0F - max - min)
                : difference / (max + min);
        final float hue;
        if (max == r) {
            hue = (g - b) / difference + (g < b ? 6.0F : 0.0F);
        } else if (max == g) {
            hue = (b - r) / difference + 2.0F;
        } else {
            hue = (r - g) / difference + 4.0F;
        }
        return new float[]{hue * 60.0F, saturation, lightness};
    }

    private static int[] hslToRgb(final float hue, final float saturation, final float lightness) {
        if (saturation == 0.0F) {
            final int grey = Math.round(lightness * 255.0F);
            return new int[]{grey, grey, grey};
        }
        final float q = lightness < 0.5F
                ? lightness * (1.0F + saturation)
                : lightness + saturation - lightness * saturation;
        final float p = 2.0F * lightness - q;
        return new int[]{
                Math.round(hueToRgb(p, q, hue / 360.0F + 1.0F / 3.0F) * 255.0F),
                Math.round(hueToRgb(p, q, hue / 360.0F) * 255.0F),
                Math.round(hueToRgb(p, q, hue / 360.0F - 1.0F / 3.0F) * 255.0F)};
    }

    private static float hueToRgb(final float p, final float q, final float t) {
        float candidate = t;
        if (candidate < 0.0F) {
            candidate += 1.0F;
        }
        if (candidate > 1.0F) {
            candidate -= 1.0F;
        }
        if (candidate < 1.0F / 6.0F) {
            return p + (q - p) * 6.0F * candidate;
        }
        if (candidate < 1.0F / 2.0F) {
            return q;
        }
        if (candidate < 2.0F / 3.0F) {
            return p + (q - p) * (2.0F / 3.0F - candidate) * 6.0F;
        }
        return p;
    }

    private record Tint(float hueShift, float saturationScale) {
    }

    /** Exposes the protected {@link TextureAtlasSprite} constructor, which is protected by design. */
    private static final class TemplateSprite extends TextureAtlasSprite {
        private TemplateSprite(final ResourceLocation atlasLocation, final SpriteContents contents,
                               final int x, final int y, final int atlasWidth, final int atlasHeight) {
            super(atlasLocation, contents, x, y, atlasWidth, atlasHeight);
        }
    }
}
