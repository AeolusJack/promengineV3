package com.thirdexploration.promengine.core.util;

import java.util.UUID;

/**
 * 唯一ID生成器。
 */
public final class IdGenerator {

    private IdGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateWithPrefix(String prefix) {
        return prefix + "_" + generate();
    }
}