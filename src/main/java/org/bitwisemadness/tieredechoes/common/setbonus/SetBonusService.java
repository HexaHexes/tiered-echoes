package org.bitwisemadness.tieredechoes.common.setbonus;

import java.util.Collections;
import java.util.Map;

public final class SetBonusService {
    private static final SetBonusService INSTANCE = new SetBonusService();

    private SetBonusService() {
    }

    public static SetBonusService getInstance() {
        return INSTANCE;
    }

    public Map<SetBonusKey, Integer> getActiveSetCounts() {
        return Collections.emptyMap();
    }
}
