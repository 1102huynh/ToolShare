package com.toolshare.common.trace;

public final class TraceIdContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String currentTraceId() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
