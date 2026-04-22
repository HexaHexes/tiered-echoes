package org.bitwisemadness.tieredechoes.api.client;

import java.util.List;
import org.bitwisemadness.tieredechoes.client.creative.CreativePreviewEntry;

public interface ITieredEchoesClientApi {
    List<CreativePreviewEntry> getPreviewEntriesForTab(String tabKey);
}
