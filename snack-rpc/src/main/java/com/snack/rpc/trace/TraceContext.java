package com.snack.rpc.trace;

/**
 * Trace context carried across RPC calls.
 * Contains traceId (全局唯一请求ID) and spanId (调用链路层级).
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class TraceContext {
    
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final long startTime;
    
    public TraceContext(String traceId, String spanId, String parentSpanId, long startTime) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.startTime = startTime;
    }
    
    /**
     * Create a new root trace context (first call in the chain).
     */
    public static TraceContext newRoot() {
        String traceId = generateId();
        String spanId = generateId();
        return new TraceContext(traceId, spanId, null, System.currentTimeMillis());
    }
    
    /**
     * Create a child span for a downstream call.
     */
    public TraceContext childSpan() {
        return new TraceContext(this.traceId, generateId(), this.spanId, System.currentTimeMillis());
    }
    
    private static String generateId() {
        return String.format("%016x%016x", 
                System.currentTimeMillis(), 
                (long) (Math.random() * Long.MAX_VALUE));
    }
    
    /**
     * Format trace context as a string for transmission.
     * Format: traceId/spanId/parentSpanId/startTime
     */
    public String toHeader() {
        return String.format("%s/%s/%s/%d", 
                traceId, spanId, parentSpanId != null ? parentSpanId : "0", startTime);
    }
    
    /**
     * Parse trace context from header string.
     */
    public static TraceContext fromHeader(String header) {
        if (header == null || header.isEmpty()) {
            return newRoot();
        }
        String[] parts = header.split("/");
        if (parts.length < 4) {
            return newRoot();
        }
        try {
            return new TraceContext(
                    parts[0],          // traceId
                    parts[1],          // spanId
                    "0".equals(parts[2]) ? null : parts[2],  // parentSpanId
                    Long.parseLong(parts[3])  // startTime
            );
        } catch (Exception e) {
            return newRoot();
        }
    }
    
    // Getters
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public long getStartTime() { return startTime; }
    
    @Override
    public String toString() {
        return "Trace[traceId=" + traceId + ", spanId=" + spanId + ", parent=" + parentSpanId + "]";
    }
}
