package com.snack.admin.controller;

import com.snack.rpc.trace.CircuitBreaker;
import com.snack.rpc.trace.CircuitBreakerRegistry;
import com.snack.rpc.trace.TraceCollector;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API Controller for Metrics, Circuit Breakers, and Health Checks.
 * Provides endpoints for the Admin Dashboard.
 * 
 * Created by yangyang.zhao on 2026/3/23.
 */
@Controller
@RequestMapping("/api")
public class MetricsController {
    
    private final TraceCollector tracer = TraceCollector.getInstance();
    private final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.getInstance();
    
    // ====================
    // Metrics APIs
    // ====================
    
    /**
     * GET /api/metrics/all
     * Get aggregated metrics for all services.
     */
    @RequestMapping(value = "/metrics/all", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Map<String, Object>> metrics = tracer.getAllMetricsSummary();
            Map<String, Double> qpsMap = tracer.getAllServiceQPS();
            
            // Add QPS to each service
            for (Map.Entry<String, Map<String, Object>> entry : metrics.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> data = entry.getValue();
                
                // Calculate total QPS for all methods of this service
                double totalQps = 0;
                for (Map.Entry<String, Double> qpsEntry : qpsMap.entrySet()) {
                    if (qpsEntry.getKey().startsWith(serviceName + ":")) {
                        totalQps += qpsEntry.getValue();
                    }
                }
                data.put("qps", totalQps);
            }
            
            result.put("success", true);
            result.put("data", metrics);
            result.put("globalQps", tracer.getGlobalQPS());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * GET /api/metrics/service/{name}
     * Get detailed metrics for a specific service.
     */
    @RequestMapping(value = "/metrics/service/{name}", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getServiceMetrics(@PathVariable("name") String serviceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Get aggregated service metrics
            Map<String, Object> serviceMetrics = tracer.getServiceMetrics(serviceName);
            
            // Get per-method metrics
            Map<String, TraceCollector.ServiceMetrics> allMetrics = tracer.getAllMetrics();
            Map<String, Map<String, Object>> methodMetrics = new HashMap<>();
            
            for (Map.Entry<String, TraceCollector.ServiceMetrics> entry : allMetrics.entrySet()) {
                if (entry.getKey().startsWith(serviceName + ":")) {
                    String methodName = entry.getKey().substring(serviceName.length() + 1);
                    TraceCollector.ServiceMetrics m = entry.getValue();
                    Map<String, Object> methodData = new HashMap<>();
                    methodData.put("totalCalls", m.getTotalCalls());
                    methodData.put("successCalls", m.getSuccessCalls());
                    methodData.put("failureCalls", m.getFailureCalls());
                    methodData.put("successRate", m.getSuccessRate());
                    methodData.put("avgLatency", m.getAvgLatency());
                    methodData.put("minLatency", m.getMinLatency());
                    methodData.put("maxLatency", m.getMaxLatency());
                    methodData.put("p50", m.getP50());
                    methodData.put("p90", m.getP90());
                    methodData.put("p99", m.getP99());
                    methodData.put("qps", tracer.getServiceQPS(serviceName, methodName));
                    methodMetrics.put(methodName, methodData);
                }
            }
            
            result.put("success", true);
            result.put("serviceName", serviceName);
            result.put("aggregatedMetrics", serviceMetrics);
            result.put("methodMetrics", methodMetrics);
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    // ====================
    // Traces APIs
    // ====================
    
    /**
     * GET /api/traces/recent
     * Get recent call traces/span list.
     */
    @RequestMapping(value = "/traces/recent", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getRecentTraces(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String serviceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TraceCollector.Span> spans;
            if (serviceName != null && !serviceName.isEmpty()) {
                // Filter by service name
                List<TraceCollector.Span> allSpans = tracer.getRecentSpans(limit * 10);
                spans = new ArrayList<>();
                for (TraceCollector.Span span : allSpans) {
                    if (span.serviceName.equals(serviceName)) {
                        spans.add(span);
                        if (spans.size() >= limit) break;
                    }
                }
            } else {
                spans = tracer.getRecentSpans(limit);
            }
            
            result.put("success", true);
            result.put("data", spans);
            result.put("total", spans.size());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * GET /api/traces/{traceId}
     * Get a specific trace by traceId.
     */
    @RequestMapping(value = "/traces/{traceId}", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getTrace(@PathVariable("traceId") String traceId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TraceCollector.Span> spans = tracer.getTrace(traceId);
            result.put("success", true);
            result.put("data", spans);
            result.put("total", spans.size());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    // ====================
    // Circuit Breaker APIs
    // ====================
    
    /**
     * GET /api/circuitbreakers
     * Get all circuit breaker states and configurations.
     */
    @RequestMapping(value = "/circuitbreakers", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getAllCircuitBreakers() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> stats = circuitBreakerRegistry.getAllStats();
            Map<String, Integer> stateCounts = circuitBreakerRegistry.getStateCounts();
            
            result.put("success", true);
            result.put("data", stats);
            result.put("stateCounts", stateCounts);
            result.put("totalCount", stats.size());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * GET /api/circuitbreakers/{serviceName}
     * Get circuit breaker state for a specific service.
     */
    @RequestMapping(value = "/circuitbreakers/{serviceName}", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getCircuitBreaker(@PathVariable("serviceName") String serviceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            CircuitBreaker cb = circuitBreakerRegistry.get(serviceName);
            if (cb == null) {
                result.put("success", false);
                result.put("message", "Circuit breaker not found for " + serviceName);
                return result;
            }
            
            result.put("success", true);
            result.put("data", cb.getStats());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * PUT /api/circuitbreakers/{serviceName}/config
     * Update circuit breaker configuration.
     */
    @RequestMapping(value = "/circuitbreakers/{serviceName}/config", method = RequestMethod.PUT)
    @ResponseBody
    public Map<String, Object> updateCircuitBreakerConfig(
            @PathVariable("serviceName") String serviceName,
            @RequestParam(defaultValue = "5") int failureThreshold,
            @RequestParam(defaultValue = "30000") long breakDurationMs,
            @RequestParam(defaultValue = "3") int halfOpenMaxTrials) {
        Map<String, Object> result = new HashMap<>();
        try {
            CircuitBreaker cb = circuitBreakerRegistry.updateConfig(
                    serviceName, failureThreshold, breakDurationMs, halfOpenMaxTrials);
            
            result.put("success", true);
            result.put("message", "Circuit breaker configuration updated for " + serviceName);
            result.put("data", cb.getStats());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * POST /api/circuitbreakers/{serviceName}/reset
     * Reset circuit breaker to closed state.
     */
    @RequestMapping(value = "/circuitbreakers/{serviceName}/reset", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> resetCircuitBreaker(@PathVariable("serviceName") String serviceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            CircuitBreaker cb = circuitBreakerRegistry.get(serviceName);
            if (cb == null) {
                result.put("success", false);
                result.put("message", "Circuit breaker not found for " + serviceName);
                return result;
            }
            
            cb.reset();
            result.put("success", true);
            result.put("message", "Circuit breaker reset for " + serviceName);
            result.put("data", cb.getStats());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * POST /api/circuitbreakers/reset-all
     * Reset all circuit breakers.
     */
    @RequestMapping(value = "/circuitbreakers/reset-all", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> resetAllCircuitBreakers() {
        Map<String, Object> result = new HashMap<>();
        try {
            circuitBreakerRegistry.resetAll();
            result.put("success", true);
            result.put("message", "All circuit breakers have been reset");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    // ====================
    // Health Check APIs
    // ====================
    
    /**
     * GET /api/health
     * System health check endpoint.
     */
    @RequestMapping(value = "/health", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
            // Check components
            Map<String, Object> components = new HashMap<>();
            
            // Trace collector
            Map<String, Object> tracerHealth = new HashMap<>();
            tracerHealth.put("status", "UP");
            tracerHealth.put("recentSpanCount", tracer.getRecentSpans(0).size());
            tracerHealth.put("enabled", true);
            components.put("traceCollector", tracerHealth);
            
            // Circuit breaker registry
            Map<String, Object> cbHealth = new HashMap<>();
            cbHealth.put("status", "UP");
            cbHealth.put("circuitBreakerCount", circuitBreakerRegistry.size());
            cbHealth.put("stateCounts", circuitBreakerRegistry.getStateCounts());
            components.put("circuitBreakerRegistry", cbHealth);
            
            // System info
            Map<String, Object> system = new HashMap<>();
            Runtime runtime = Runtime.getRuntime();
            system.put("totalMemory", runtime.totalMemory());
            system.put("freeMemory", runtime.freeMemory());
            system.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            system.put("maxMemory", runtime.maxMemory());
            system.put("availableProcessors", runtime.availableProcessors());
            components.put("system", system);
            
            health.put("components", components);
            
            result.put("success", true);
            result.put("data", health);
        } catch (Exception e) {
            result.put("success", false);
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * GET /api/health/liveness
     * Kubernetes-style liveness probe.
     */
    @RequestMapping(value = "/health/liveness", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> liveness() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    /**
     * GET /api/health/readiness
     * Kubernetes-style readiness probe.
     */
    @RequestMapping(value = "/health/readiness", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> readiness() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    // ====================
    // Dashboard Data APIs
    // ====================
    
    /**
     * GET /api/dashboard/summary
     * Get dashboard summary data for real-time monitoring.
     */
    @RequestMapping(value = "/dashboard/summary", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> summary = new HashMap<>();
            
            // Global QPS
            summary.put("globalQps", tracer.getGlobalQPS());
            
            // Service metrics summary
            Map<String, Map<String, Object>> allMetrics = tracer.getAllMetricsSummary();
            Map<String, Double> qpsMap = tracer.getAllServiceQPS();
            
            // Calculate global metrics
            long totalCalls = 0, successCalls = 0, failureCalls = 0;
            double avgLatency = 0;
            for (Map<String, Object> metrics : allMetrics.values()) {
                totalCalls += ((Number) metrics.get("totalCalls")).longValue();
                successCalls += ((Number) metrics.get("successCalls")).longValue();
                failureCalls += ((Number) metrics.get("failureCalls")).longValue();
                avgLatency += ((Number) metrics.get("avgLatency")).doubleValue();
            }
            avgLatency = allMetrics.isEmpty() ? 0 : avgLatency / allMetrics.size();
            
            summary.put("totalCalls", totalCalls);
            summary.put("successCalls", successCalls);
            summary.put("failureCalls", failureCalls);
            summary.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls * 100 : 0);
            summary.put("avgLatency", avgLatency);
            
            // Circuit breaker summary
            summary.put("circuitBreakerStates", circuitBreakerRegistry.getStateCounts());
            summary.put("totalCircuitBreakers", circuitBreakerRegistry.size());
            
            // Recent traces count
            summary.put("recentSpanCount", tracer.getRecentSpans(0).size());
            
            // Top services by QPS
            List<Map.Entry<String, Double>> sortedQps = new ArrayList<>(qpsMap.entrySet());
            sortedQps.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<Map<String, Object>> topServices = new ArrayList<>();
            for (int i = 0; i < Math.min(5, sortedQps.size()); i++) {
                Map<String, Object> svc = new HashMap<>();
                svc.put("service", sortedQps.get(i).getKey());
                svc.put("qps", sortedQps.get(i).getValue());
                topServices.add(svc);
            }
            summary.put("topServices", topServices);
            
            result.put("success", true);
            result.put("data", summary);
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    // ====================
    // Reset APIs
    // ====================
    
    /**
     * POST /api/reset
     * Reset all tracing data.
     */
    @RequestMapping(value = "/reset", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> resetTracingData() {
        Map<String, Object> result = new HashMap<>();
        try {
            tracer.reset();
            result.put("success", true);
            result.put("message", "All tracing data has been cleared");
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
