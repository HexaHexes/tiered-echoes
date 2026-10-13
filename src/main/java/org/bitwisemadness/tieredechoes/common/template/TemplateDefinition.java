package org.bitwisemadness.tieredechoes.common.template;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One named template: the thing a tier puts in the smithing table's template slot.
 *
 * <p>A template is an entity of its own rather than a property of a tier. Nothing here knows which
 * tier spends it, so a single declaration can serve every domain and every rung that names it — the
 * branch a fork offers is told apart by <em>which</em> template it asks for, not by which tier the
 * declaration happened to belong to.</p>
 *
 * @param id                the template's name. This is what a tier refers to, and what the
 *                          conventional {@code <tier>_upgrade_template} item is called when the
 *                          mod ships the template itself.
 * @param item              what the template slot consumes. Absent, and the declaration's own id
 *                          names the item instead, which is how a tier can point straight at an
 *                          item without a declaration behind it at all.
 * @param itemModelOverride an optional replacement model for that item
 * @param textureOverride   an optional replacement texture for that model
 */
public record TemplateDefinition(
        ResourceLocation id,
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> itemModelOverride,
        Optional<ResourceLocation> textureOverride
) {
    public TemplateDefinition {
        item = item == null ? Optional.empty() : item;
        itemModelOverride = itemModelOverride == null ? Optional.empty() : itemModelOverride;
        textureOverride = textureOverride == null ? Optional.empty() : textureOverride;
    }
}
