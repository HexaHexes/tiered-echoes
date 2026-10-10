package org.bitwisemadness.tieredechoes.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import org.bitwisemadness.tieredechoes.TieredEchoes;
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
     * Templates are keyed by tier alone rather than by tier and domain, which is the whole point of
     * the one-pair-per-tier layout: a sword and a pickaxe of the same tier spend the same template.
     */
    @GameTest
    public static void everyBuiltInTierResolvesBothTemplateItems(final GameTestHelper helper) {
        for (final String path : List.of("common", "uncommon", "rare", "epic")) {
            final TierDefinition tier = GameTestTiers.tier(helper, TierDomain.WEAPON, path);
            final Optional<Item> upgrade = TemplateService.getInstance().templateItem(tier, false);
            final Optional<Item> downgrade = TemplateService.getInstance().templateItem(tier, true);

            helper.assertTrue(upgrade.isPresent(), tier.id() + " has no upgrade template item registered");
            helper.assertTrue(downgrade.isPresent(), tier.id() + " has no downgrade template item registered");
            helper.assertTrue(upgrade.get() != Items.AIR && downgrade.get() != Items.AIR,
                    tier.id() + " resolved to air, which would leave the smithing slot unusable");
            helper.assertTrue(upgrade.get() != downgrade.get(),
                    tier.id() + " uses one item for both directions, so neither could be spent deliberately");
        }
        helper.succeed();
    }

    @GameTest
    public static void aDatapackCanSwapTheTemplateItemWithoutRegisteringAnItem(final GameTestHelper helper) {
        final TemplateService service = TemplateService.getInstance();
        final List<TemplateDefinition> declaredBefore = service.declaredTemplates();
        final TierDefinition common = GameTestTiers.tier(helper, TierDomain.WEAPON, "common");
        try {
            service.replaceAll(List.of(new TemplateDefinition(
                    new ResourceLocation(TieredEchoes.MOD_ID, "custom_common_upgrade"),
                    common.id(),
                    false,
                    Optional.of(new ResourceLocation("minecraft", "nether_star")),
                    Optional.empty(),
                    Optional.empty())));

            helper.assertTrue(service.templateItem(common, false).filter(Items.NETHER_STAR::equals).isPresent(),
                    "a tier_templates declaration should win over the registered fallback item");
            helper.assertTrue(service.templateItem(common, true).isPresent(),
                    "declaring one direction must not remove the other");
        } finally {
            service.replaceAll(declaredBefore);
        }
        helper.succeed();
    }
}
