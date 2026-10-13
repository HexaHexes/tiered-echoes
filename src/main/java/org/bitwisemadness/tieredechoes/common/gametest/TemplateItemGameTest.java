package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
import org.bitwisemadness.tieredechoes.common.registry.TEItems;
import org.bitwisemadness.tieredechoes.common.template.TemplateDefinition;
import org.bitwisemadness.tieredechoes.common.template.TemplateService;
import org.bitwisemadness.tieredechoes.common.tier.TierDefinition;
import org.bitwisemadness.tieredechoes.common.tier.TierDomain;

import java.util.List;
import java.util.Optional;

/**
 * {@code TemplateService} resolves template items through {@code ForgeRegistries}, which is only
 * populated once a real registry has been built — so this is the one template behaviour a unit
 * test cannot reach.
 */
@GameTestHolder(TieredEchoes.MOD_ID)
public final class TemplateItemGameTest {

    private TemplateItemGameTest() {
    }

    /**
     * Templates are named entities rather than per-domain ones, which is the whole point of the
     * one-per-tier layout: a sword and a pickaxe of the same tier spend the same template.
     *
     * <p>Upgrade only. Downgrade templates are not registered, because the shipped ladder declares
     * no way down — and a registered item with nothing to spend it on would be a creative-tab entry
     * advertising a transition the table refuses. A pack that adds the route declares the template
     * to spend on it, which is the next test.</p>
     */
    @GameTest
    public static void everyBuiltInTierResolvesItsUpgradeTemplateAndNoDowngradeOne(final GameTestHelper helper) {
        for (final String path : List.of("common", "uncommon", "rare", "epic", "legendary")) {
            final TierDefinition tier = GameTestTiers.tier(helper, TierDomain.WEAPON, path);
            final Optional<Item> upgrade = TemplateService.getInstance().templateItem(tier, false);
            final Optional<Item> downgrade = TemplateService.getInstance().templateItem(tier, true);

            helper.assertTrue(upgrade.isPresent(), tier.id() + " has no upgrade template item registered");
            helper.assertTrue(upgrade.get() != Items.AIR,
                    tier.id() + " resolved to air, which would leave the smithing slot unusable");
            helper.assertTrue(downgrade.isEmpty(),
                    tier.id() + " resolved a downgrade template, but the shipped ladder declares no way down");
        }
        helper.succeed();
    }

    /**
     * The point of a template being an entity of its own: a tier picks one out by name, so a pack
     * can give a rung a template of its own without registering an item — and can point two rungs
     * at one declaration if that is what it means to do.
     *
     * <p>Also the point of naming nothing: a tier that declares no template must keep spending the
     * built-in item, because that is the only reason a ladder can ship at all.</p>
     */
    @GameTest
    public static void aDatapackCanSwapTheTemplateItemWithoutRegisteringAnItem(final GameTestHelper helper) {
        final TemplateService service = TemplateService.getInstance();
        final List<TemplateDefinition> declaredBefore = service.declaredTemplates();
        final TierDefinition common = GameTestTiers.tier(helper, TierDomain.WEAPON, "common");
        final TierDefinition naming = GameTestTiers.naming(common,
                new ResourceLocation(TieredEchoes.MOD_ID, "custom_common_upgrade"),
                new ResourceLocation(TieredEchoes.MOD_ID, "custom_common_downgrade"));
        try {
            service.replaceAll(List.of(
                    new TemplateDefinition(
                            new ResourceLocation(TieredEchoes.MOD_ID, "custom_common_upgrade"),
                            Optional.of(new ResourceLocation("minecraft", "nether_star")),
                            Optional.empty(),
                            Optional.empty()),
                    new TemplateDefinition(
                            new ResourceLocation(TieredEchoes.MOD_ID, "custom_common_downgrade"),
                            Optional.of(new ResourceLocation("minecraft", "netherite_ingot")),
                            Optional.empty(),
                            Optional.empty())));

            helper.assertTrue(service.templateItem(naming, false).filter(Items.NETHER_STAR::equals).isPresent(),
                    "a tier that names a template should spend that declaration's item");
            helper.assertTrue(service.templateItem(naming, true).filter(Items.NETHERITE_INGOT::equals).isPresent(),
                    "the downward direction is named the same way, whether or not it is shipped");
            helper.assertTrue(service.templateItem(common, false)
                            .filter(TEItems.UPGRADE_TEMPLATES.get("common").get()::equals).isPresent(),
                    "a tier that names nothing must keep spending the built-in item");
        } finally {
            service.replaceAll(declaredBefore);
        }
        helper.succeed();
    }
}
