package org.bitwisemadness.tieredechoes.common.template;

import java.util.Collections;
import java.util.List;

public final class TemplateService {
    private static final TemplateService INSTANCE = new TemplateService();

    private TemplateService() {
    }

    public static TemplateService getInstance() {
        return INSTANCE;
    }

    public List<TemplateDefinition> getAllTemplates() {
        return Collections.emptyList();
    }
}
