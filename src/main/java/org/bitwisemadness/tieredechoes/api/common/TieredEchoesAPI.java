package org.bitwisemadness.tieredechoes.api.common;

import lombok.experimental.UtilityClass;
import org.bitwisemadness.tieredechoes.common.path.PathService;
import org.bitwisemadness.tieredechoes.common.tier.TierService;
import org.bitwisemadness.tieredechoes.common.upgrade.UpgradeService;

@UtilityClass
public class TieredEchoesAPI {

    public static ITierService tiers() {
        return TierService.getInstance();
    }

    public static IPathService paths() {
        return PathService.getInstance();
    }

    public static IUpgradeService upgrades() {
        return UpgradeService.getInstance();
    }
}
