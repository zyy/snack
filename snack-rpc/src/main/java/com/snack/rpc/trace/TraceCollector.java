package com.snack.rpc.trace;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.server.ServerHandler;
import com.typesafe.config.Config;
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
 * - Integration with CircuitBreakerRegistry for circuit breaker state
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class TraceCollector {
    private static final Logger logger = LoggerFactory.getLogger(TraceCollector.class);
    
    // In-memory trace storage (in production, use a real metrics library)
    private final ConcurrentLinkedDeque<Span> recentSpans = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ServiceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    private final int maxSpans; // keep last N spans in memory
    private final int latencyWindow; // window size for latency percentile calculations
    
    // Config
    private final boolean enabled;
    private final int sampleRate; // 0-100, percent of calls to trace
    
    // QPS calculation
    private final ConcurrentLinkedDeque<Long> timestamps = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> serviceTimestamps = new ConcurrentHashMap<>();
    
    public TraceCollector(boolean enabled, int sampleRate) {
        this(enabled, sampleRate, 10000, 1000);
    }
    
    public TraceCollector(boolean enabled, int sampleRate, int maxSpans, int latencyWindow) {
        this.enabled = enabled;
        this.sampleRate = Math.max(0, Math.min(100, sampleRate));
        this.maxSpans = maxSpans > 0 ? maxSpans : 10000;
        this.latencyWindow = latencyWindow > 0 ? latencyWindow : 1000;
    }
    
    /**
     * Create TraceCollector from configuration.
     */
    public static TraceCollector fromConfig(Config config) {
        if (config == null) {
            return new TraceCollector(true, 100);
        }
        boolean enabled = config.hasPath("tracing.enabled") 
                ? config.getBoolean("tracing.enabled") : true;
        int sampleRate = config.hasPath("tracing.sampleRate") 
                ? config.getInt("tracing.sampleRate") : 100;
        int maxSpans = config.hasPath("tracing.maxSpans") 
                ? config.getInt("tracing.maxSpans") : 10000;
        int latencyWindow = config.hasPath("tracing.latencyWindow") 
                ? config.getInt("tracing.latencyWindow") : 1000;
        return new TraceCollector(enabled, sampleRate, maxSpans, latencyWindow);
    }
    
    private static volatile TraceCollector INSTANCE = null;
    
    /**
     * Get the global TraceCollector instance, initialized from config if needed.
     */
    public static TraceCollector getInstance() {
        if (INSTANCE == null) {
            synchronized (TraceCollector.class) {
                if (INSTANCE == null) {
                    try {
                        Config config = RpcServer.getConfig();
                        INSTANCE = fromConfig(config);
                        logger.info("TraceCollector initialized from config: enabled={}, sampleRate={}%", 
                                INSTANCE.enabled, INSTANCE.sampleRate);
                    } catch (Exception e) {
                        logger.warn("Failed to load tracing config, using defaults", e);
                        INSTANCE = new TraceCollector(true, 100);
                    }
                }
            }
        }
        return INSTANCE;
    }
    

    
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
        recordTimestamp(serviceName, methodName);
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
        recordTimestamp(serviceName, methodName);
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
    
    private void recordTimestamp(String serviceName, String methodName) {
        long now = System.currentTimeMillis();
        timestamps.addLast(now);
        // Clean old timestamps (older than 1 minute)
        while (!timestamps.isEmpty() && timestamps.peekFirst() < now - 60000) {
            timestamps.removeFirst();
        }
        
        String key = serviceName + ":" + methodName;
        ConcurrentLinkedDeque<Long> serviceTs = serviceTimestamps.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        serviceTs.addLast(now);
        while (!serviceTs.isEmpty() && serviceTs.peekFirst() < now - 60000) {
            serviceTs.removeFirst();
        }
    }
    
    /**
     * Get QPS for all services combined.
     */
    public double getGlobalQPS() {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;
        
        int count = 0;
        for (Long ts : timestamps) {
            if (ts >= oneMinuteAgo) count++;
        }
        return count / 60.0; // per second
    }
    
    /**
     * Get QPS for a specific service method.
     */
    public double getServiceQPS(String serviceName, String methodName) {
        String key = serviceName + ":" + methodName;
        ConcurrentLinkedDeque<Long> ts = serviceTimestamps.get(key);
        if (ts == null) return 0;
        
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;
        
        int count = 0;
        for (Long t : ts) {
            if (t >= oneMinuteAgo) count++;
        }
        return count / 60.0;
    }
    
    /**
     * Get all service QPS values.
     */
    public Map<String, Double> getAllServiceQPS() {
        Map<String, Double> result = new HashMap<>();
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60000;
        
        for (Map.Entry<String, ConcurrentLinkedDeque<Long>> entry : serviceTimestamps.entrySet()) {
            int count = 0;
            for (Long ts : entry.getValue()) {
                if (ts >= oneMinuteAgo) count++;
            }
            result.put(entry.getKey(), count / 60.0);
        }
        return result;
    }
    
    /**
     * Get recent traces (for trace list).
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
     * Get aggregated metrics for all services.
     */
    public Map<String, Map<String, Object>> getAllMetricsSummary() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        // Add service names from recent spans
        Set<String> services = new HashSet<>();
        for (Span span : recentSpans) {
            services.add(span.serviceName);
        }
        
        for (String serviceName : services) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("serviceName", serviceName);
            
            // Calculate aggregated metrics for this service
            long totalCalls = 0, successCalls = 0, failureCalls = 0;
            double totalLatency = 0;
            List<Long> allLatencies = new ArrayList<>();
            
            for (Span span : recentSpans) {
                if (span.serviceName.equals(serviceName)) {
                    totalCalls++;
                    if (span.success) successCalls++;
                    else failureCalls++;
                    totalLatency += span.durationMs;
                    allLatencies.add(span.durationMs);
                }
            }
            
            summary.put("totalCalls", totalCalls);
            summary.put("successCalls", successCalls);
            summary.put("failureCalls", failureCalls);
            summary.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0);
            summary.put("avgLatency", totalCalls > 0 ? totalLatency / totalCalls : 0);
            summary.put("qps", getServiceQPS(serviceName, ""));
            
            // Calculate percentiles
            if (!allLatencies.isEmpty()) {
                Collections.sort(allLatencies);
                int size = allLatencies.size();
                summary.put("p50", allLatencies.get((int)(size * 0.5) - 1));
                summary.put("p90", allLatencies.get((int)(size * 0.9) - 1));
                summary.put("p99", allLatencies.get(Math.min((int)(size * 0.99), size - 1)));
            } else {
                summary.put("p50", 0.0);
                summary.put("p90", 0.0);
                summary.put("p99", 0.0);
            }
            
            result.put(serviceName, summary);
        }
        
        return result;
    }
    
    /**
     * Get metrics for all services (legacy method, returns ServiceMetrics objects).
     */
    public Map<String, ServiceMetrics> getAllMetrics() {
        return new HashMap<>(serviceMetrics);
    }
    
    /**
     * Get metrics for a specific service method.
     */
    public ServiceMetrics getMetrics(String serviceName, String methodName) {
        return serviceMetrics.get(serviceName + ":" + methodName);
    }
    
    /**
     * Get metrics for a specific service (aggregated over all methods).
     */
    public Map<String, Object> getServiceMetrics(String serviceName) {
        Map<String, Object> result = new HashMap<>();
        result.put("serviceName", serviceName);
        
        long totalCalls = 0, successCalls = 0, failureCalls = 0;
        List<Long> allLatencies = new ArrayList<>();
        
        for (Span span : recentSpans) {
            if (span.serviceName.equals(serviceName)) {
                totalCalls++;
                if (span.success) successCalls++;
                else failureCalls++;
                allLatencies.add(span.durationMs);
            }
        }
        
        result.put("totalCalls", totalCalls);
        result.put("successCalls", successCalls);
        result.put("failureCalls", failureCalls);
        result.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0);
        result.put("qps", getServiceQPS(serviceName, ""));
        
        if (!allLatencies.isEmpty()) {
            Collections.sort(allLatencies);
            int size = allLatencies.size();
            result.put("avgLatency", allLatencies.stream().mapToLong(Long::longValue).average().orElse(0));
            result.put("minLatency", allLatencies.get(0));
            result.put("maxLatency", allLatencies.get(size - 1));
            result.put("p50", allLatencies.get((int)(size * 0.5) - 1));
            result.put("p90", allLatencies.get((int)(size * 0.9) - 1));
            result.put("p99", allLatencies.get(Math.min((int)(size * 0.99), size - 1)));
        } else {
            result.put("avgLatency", 0.0);
            result.put("minLatency", 0);
            result.put("maxLatency", 0);
            result.put("p50", 0.0);
            result.put("p90", 0.0);
            result.put("p99", 0.0);
        }
        
        return result;
    }
    
    /**
     * Clear all stored data.
     */
    public void reset() {
        recentSpans.clear();
        serviceMetrics.clear();
        timestamps.clear();
        serviceTimestamps.clear();
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
    public class ServiceMetrics {
        private final AtomicLong totalCalls = new AtomicLong();
        private final AtomicLong successCalls = new AtomicLong();
        private final AtomicLong failureCalls = new AtomicLong();
        private final AtomicLong totalLatency = new AtomicLong();
        private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxLatency = new AtomicLong();
        private final ConcurrentLinkedDeque<Long> latencies = new ConcurrentLinkedDeque<>();
        private final int latencyWindow;
        
        public ServiceMetrics() {
            this.latencyWindow = TraceCollector.this.latencyWindow;
        }
        
        public ServiceMetrics(int latencyWindow) {
            this.latencyWindow = latencyWindow > 0 ? latencyWindow : 1000;
        }
        
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
            while (latencies.size() > latencyWindow) {
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
