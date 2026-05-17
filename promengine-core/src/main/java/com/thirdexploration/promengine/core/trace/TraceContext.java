package com.thirdexploration.promengine.core.trace;

import org.slf4j.MDC;
import java.util.UUID;

public final class TraceContext {
    private static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {}

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String generateTraceId() {
        return "trace-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}