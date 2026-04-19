package com.thirdexploration.promengine.core.util;

import java.util.Collection;

/**
 * 校验工具类。
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static void requireNonBlank(String str, String fieldName) {
        if (isBlank(str)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}