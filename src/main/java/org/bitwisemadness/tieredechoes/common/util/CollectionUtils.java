package org.bitwisemadness.tieredechoes.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CollectionUtils {

    public static <T> List<T> mutableCopy(final Collection<T> values) {
        return new ArrayList<>(values);
    }
}
