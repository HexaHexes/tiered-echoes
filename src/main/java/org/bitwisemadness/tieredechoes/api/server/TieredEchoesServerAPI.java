package org.bitwisemadness.tieredechoes.api.server;

import org.bitwisemadness.tieredechoes.common.data.validation.ValidationReporter;

public final class TieredEchoesServerAPI implements ITieredEchoesServerApi {
    private static final TieredEchoesServerAPI INSTANCE = new TieredEchoesServerAPI();

    private TieredEchoesServerAPI() {
    }

    public static ITieredEchoesServerApi getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean validateRuntimeState() {
        return ValidationReporter.getLastReport() != null;
    }
}
