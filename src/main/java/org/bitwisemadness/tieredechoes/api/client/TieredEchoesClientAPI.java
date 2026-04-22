package org.bitwisemadness.tieredechoes.api.client;

import java.util.List;
import org.bitwisemadness.tieredechoes.client.creative.CreativePreviewCatalog;
import org.bitwisemadness.tieredechoes.client.creative.CreativePreviewEntry;

public final class TieredEchoesClientAPI implements ITieredEchoesClientApi {
    private static final TieredEchoesClientAPI INSTANCE = new TieredEchoesClientAPI();

    private TieredEchoesClientAPI() {
    }

    public static ITieredEchoesClientApi getInstance() {
        return INSTANCE;
    }

    @Override
    public List<CreativePreviewEntry> getPreviewEntriesForTab(final String tabKey) {
        return CreativePreviewCatalog.getInstance().getEntriesForTab(tabKey);
    }
}
