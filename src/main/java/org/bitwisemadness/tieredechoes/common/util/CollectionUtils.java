package org.bitwisemadness.tieredechoes.common.util;

import lombok.experimental.UtilityClass;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@UtilityClass
public class CollectionUtils {

    public static <T> List<T> mutableCopy(final Collection<T> values) {
        return new ArrayList<>(values);
    }
}
