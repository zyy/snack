package com.snack.rpc.trace;

import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.server.ServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC call tracing and metrics collector.
 * 
 * Features:
 * - Trace context propagation across RPC calls
 * - Per-call latency, success/failure recording
 * - Aggregated metrics: QPS, latency percentiles, error rate
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class TraceCollector {
    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);
    
    // In-memory trace storage (in production, use a real metrics library)
    private final ConcurrentLinkedDeque<Span> recentSpans = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ServiceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    private final int maxSpans = 10000; // keep last 10k spans in memory
    
    // Config
    private final boolean enabled;
    private final int sampleRate; // 0-100, percent of calls to trace
    
    public TraceCollector(boolean enabled, int sampleRate) {
        this.enabled = enabled;
        this.sampleRate = Math.max(0, Math.min(100, sampleRate));
    }
    
    public static TraceCollector INSTANCE = new TraceCollector(true, 100);
    
    /**
     * Record a server-side span (when RPC request is received and processed).
     */
    public void recordServerSpan(TraceContext context, String serviceName, String methodName,
            boolean success, long durationMs, String errorInfo) {
        if (!enabled || !shouldSample()) return;
        
        Span span = new Span();
        span.traceId = context.getTraceId();
        span.spanId = context.getSpanId();
        span.parentSpanId = context.getParentSpanId();
        span.serviceName = serviceName;
        span.methodName = methodName;
        span.startTime = context.getStartTime();
        span.durationMs = durationMs;
        span.success = success;
        span.errorInfo = errorInfo;
        span.role = "server";
        
        addSpan(span);
        updateMetrics(span);
    }
    
    /**
     * Record a client-side span (when RPC call is made).
     */
    public void recordClientSpan(TraceContext context, String serviceName, String methodName,
            String targetAddress, boolean success, long durationMs, String errorInfo) {
        if (!enabled || !shouldSample()) return;
        
        Span span = new Span();
        span.traceId = context.getTraceId();
        span.spanId = context.getSpanId();
        span.parentSpanId = context.getParentSpanId();
        span.serviceName = serviceName;
        span.methodName = methodName;
        span.targetAddress = targetAddress;
        span.startTime = context.getStartTime();
        span.durationMs = durationMs;
        span.success = success;
        span.errorInfo = errorInfo;
        span.role = "client";
        
        addSpan(span);
        updateMetrics(span);
    }
    
    private boolean shouldSample() {
        return sampleRate >= 100 || Math.random() * 100 < sampleRate;
    }
    
    private void addSpan(Span span) {
        recentSpans.addLast(span);
        while (recentSpans.size() > maxSpans) {
            recentSpans.removeFirst();
        }
    }
    
    private void updateMetrics(Span span) {
        String key = span.serviceName + ":" + span.methodName;
        ServiceMetrics metrics = serviceMetrics.computeIfAbsent(key, k -> new ServiceMetrics());
        metrics.record(span);
    }
    
    /**
     * Get all recent spans.
     */
    public List<Span> getRecentSpans(int limit) {
        List<Span> result = new ArrayList<>();
        int count = 0;
        for (Span span : recentSpans) {
            result.add(span);
            if (++count >= limit) break;
        }
        return result;
    }
    
    /**
     * Get traces for a specific traceId.
     */
    public List<Span> getTrace(String traceId) {
        List<Span> result = new ArrayList<>();
        for (Span span : recentSpans) {
            if (span.traceId.equals(traceId)) {
                result.add(span);
            }
        }
        return result;
    }
    
    /**
     * Get metrics for all services.
     */
    public Map<String, ServiceMetrics> getAllMetrics() {
        return new HashMap<>(serviceMetrics);
    }
    
    /**
     * Get metrics for a specific service.
     */
    public ServiceMetrics getMetrics(String serviceName, String methodName) {
        return serviceMetrics.get(serviceName + ":" + methodName);
    }
    
    /**
     * Clear all stored data.
     */
    public void reset() {
        recentSpans.clear();
        serviceMetrics.clear();
    }
    
    // ====================
    // Data classes
    // ====================
    
    public static class Span {
        public String traceId;
        public String spanId;
        public String parentSpanId;
        public String serviceName;
        public String methodName;
        public String targetAddress;
        public String role; // "client" or "server"
        public long startTime;
        public long durationMs;
        public boolean success;
        public String errorInfo;
        
        @Override
        public String toString() {
            return String.format("Span[%s %s.%s %s %dms %s]",
                    role, serviceName, methodName, 
                    success ? "OK" : "FAIL",
                    durationMs, errorInfo != null ? errorInfo : "");
        }
    }
    
    /**
     * Aggregated metrics for a service method.
     */
    public static class ServiceMetrics {
        private final AtomicLong totalCalls = new AtomicLong();
        private final AtomicLong successCalls = new AtomicLong();
        private final AtomicLong failureCalls = new AtomicLong();
        private final AtomicLong totalLatency = new AtomicLong();
        private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxLatency = new AtomicLong();
        private final ConcurrentLinkedDeque<Long> latencies = new ConcurrentLinkedDeque<>();
        private static final int LATENCY_WINDOW = 1000; // last 1000 latencies for percentile
        
        public void record(Span span) {
            totalCalls.incrementAndGet();
            if (span.success) {
                successCalls.incrementAndGet();
            } else {
                failureCalls.incrementAndGet();
            }
            
            totalLatency.addAndGet(span.durationMs);
            minLatency.updateAndGet(v -> Math.min(v, span.durationMs));
            maxLatency.updateAndGet(v -> Math.max(v, span.durationMs));
            
            latencies.addLast(span.durationMs);
            while (latencies.size() > LATENCY_WINDOW) {
                latencies.removeFirst();
            }
        }
        
        public long getTotalCalls() { return totalCalls.get(); }
        public long getSuccessCalls() { return successCalls.get(); }
        public long getFailureCalls() { return failureCalls.get(); }
        
        public double getSuccessRate() {
            long total = totalCalls.get();
            return total > 0 ? (double) successCalls.get() / total * 100 : 0;
        }
        
        public double getAvgLatency() {
            long total = totalCalls.get();
            return total > 0 ? (double) totalLatency.get() / total : 0;
        }
        
        public long getMinLatency() {
            long v = minLatency.get();
            return v == Long.MAX_VALUE ? 0 : v;
        }
        
        public long getMaxLatency() { return maxLatency.get(); }
        
        public double getPercentile(double p) {
            if (latencies.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
            idx = Math.max(0, Math.min(idx, sorted.size() - 1));
            return sorted.get(idx);
        }
        
        public double getP50() { return getPercentile(50); }
        public double getP90() { return getPercentile(90); }
        public double getP99() { return getPercentile(99); }
        
        @Override
        public String toString() {
            return String.format(
                    "Metrics[total=%d, success=%.1f%%, avg=%.1fms, p90=%.1fms, p99=%.1fms]",
                    getTotalCalls(), getSuccessRate(), getAvgLatency(), getP90(), getP99());
        }
    }
}
