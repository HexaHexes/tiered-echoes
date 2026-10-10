package org.bitwisemadness.tieredechoes.common.policy;

import org.bitwisemadness.tieredechoes.common.config.TieredEchoesConfig;
import org.bitwisemadness.tieredechoes.testutil.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyServiceTest {

    private PolicyService service;

    @BeforeEach
    void setUp() {
        TestConfig.load();
        this.service = PolicyService.getInstance();
    }

    @AfterEach
    void tearDown() {
        this.service.replace(TierPolicy.defaults());
        TestConfig.resetToDefaults();
    }

    @Test
    void startsFromTheDefaults() {
        this.service.replace(TierPolicy.defaults());

        assertEquals(TierPolicy.defaults(), this.service.get());
        assertTrue(this.service.get().autoUpgradeEnabled());
        assertTrue(this.service.get().upgradeRecipesEnabled());
    }

    @Test
    void replacingWithNullFallsBackToDefaultsInsteadOfNpeing() {
        this.service.replace(new TierPolicy(TierPolicy.Mode.AUTO_ONLY, TierPolicy.MultiPathResolution.FIRST,
                false, true, Boolean.TRUE));
        this.service.replace(null);

        assertEquals(TierPolicy.defaults(), this.service.get());
    }

    @Test
    void aDeclaredValueWinsOverTheOperatorConfig() {
        this.service.replace(new TierPolicy(TierPolicy.Mode.HYBRID, TierPolicy.MultiPathResolution.RANDOM,
                true, false, Boolean.TRUE));
        assertTrue(this.service.consumeAttunementItem());

        this.service.replace(new TierPolicy(TierPolicy.Mode.HYBRID, TierPolicy.MultiPathResolution.RANDOM,
                true, false, Boolean.FALSE));
        assertFalse(this.service.consumeAttunementItem(), "an explicit false must beat the config default");
    }

    @Test
    void anUndeclaredValueDefersToTheConfigFile() {
        this.service.replace(TierPolicy.defaults());
        assertFalse(this.service.consumeAttunementItem(),
                "the shipped config default is false and no tier_policies file overrides it");

        TieredEchoesConfig.CONSUME_ATTUNEMENT_ITEM.set(true);
        assertTrue(this.service.consumeAttunementItem(),
                "with no datapack statement the operator's config decides");
        TieredEchoesConfig.CONSUME_ATTUNEMENT_ITEM.set(false);
    }
}
