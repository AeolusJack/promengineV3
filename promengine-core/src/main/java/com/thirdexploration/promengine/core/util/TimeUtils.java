package com.thirdexploration.promengine.core.util;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类。
 */
public final class TimeUtils {

    private TimeUtils() {}

    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public static String formatIso(Instant instant) {
        return ISO_FORMATTER.format(instant);
    }

    public static Instant parseIso(String text) {
        return Instant.from(ISO_FORMATTER.parse(text));
    }

    public static long toEpochMilli(Instant instant) {
        return instant.toEpochMilli();
    }

    public static boolean isOlderThan(Instant timestamp, Duration duration) {
        return Instant.now().minus(duration).isAfter(timestamp);
    }
}