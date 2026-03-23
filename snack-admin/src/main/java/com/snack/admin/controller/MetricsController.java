package com.snack.admin.controller;

import com.snack.admin.service.ApplicationService;
import com.snack.rpc.trace.CircuitBreaker;
import com.snack.rpc.trace.CircuitBreakerRegistry;
import com.snack.rpc.trace.TraceCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API Controller for Metrics, Circuit Breakers, and Health Checks.
 */
@Controller
@RequestMapping("/api")
public class MetricsController {
    
    private final TraceCollector tracer = TraceCollector.getInstance();
    private final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.getInstance();
    
    @Autowired(required = false)
    private ApplicationService applicationService;
    
    // ====================
    // Services APIs
    // ====================
    
    /**
     * GET /api/services
     * Get list of all registered services.
     */
    @RequestMapping(value = "/services", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getServices() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> services = new ArrayList<>();
            
            // Get services from TraceCollector
            Map<String, Map<String, Object>> metrics = tracer.getAllMetricsSummary();
            for (String serviceName : metrics.keySet()) {
                Map<String, Object> service = new HashMap<>();
                service.put("name", serviceName);
                service.put("type", "tracked");
                
                Map<String, Object> m = metrics.get(serviceName);
                service.put("totalCalls", m.getOrDefault("totalCalls", 0));
                service.put("successRate", m.getOrDefault("successRate", 0.0));
                
                // Get instance count from ZooKeeper
                if (applicationService != null) {
                    try {
                        List instances = applicationService.getServiceInstances(serviceName);
                        service.put("instances", instances != null ? instances.size() : 0);
                        service.put("instanceList", instances);
                    } catch (Exception e) {
                        service.put("instances", 0);
                    }
                } else {
                    service.put("instances", 1); // Default to 1
                }
                
                services.add(service);
            }
            
            // Also add services from circuit breakers that might not have metrics
            List<Map<String, Object>> allBreakersStats = circuitBreakerRegistry.getAllStats();
            for (Map<String, Object> cbStats : allBreakersStats) {
                String cbName = (String) cbStats.get("name");
                boolean found = false;
                for (Map<String, Object> s : services) {
                    if (s.get("name").equals(cbName)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Map<String, Object> service = new HashMap<>();
                    service.put("name", cbName);
                    service.put("type", "circuit-breaker-only");
                    service.put("instances", 0);
                    service.put("totalCalls", 0);
                    service.put("successRate", 0.0);
                    services.add(service);
                }
            }
            
            result.put("success", true);
            result.put("data", services);
            result.put("total", services.size());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * GET /api/services/{serviceName}
     * Get service details.
     */
    @RequestMapping(value = "/services/{serviceName}", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getService(@PathVariable("serviceName") String serviceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> service = new HashMap<>();
            service.put("name", serviceName);
            
            // Get metrics
            Map<String, Map<String, Object>> metrics = tracer.getAllMetricsSummary();
            if (metrics.containsKey(serviceName)) {
                service.put("metrics", metrics.get(serviceName));
            }
            
            // Get circuit breaker
            CircuitBreaker cb = circuitBreakerRegistry.get(serviceName);
            if (cb != null) {
                service.put("circuitBreaker", cb.getStats());
            }
            
            // Get instances
            if (applicationService != null) {
                List instances = applicationService.getServiceInstances(serviceName);
                service.put("instances", instances);
            }
            
            result.put("success", true);
            result.put("data", service);
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
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
            
            // Calculate totals
            long totalCalls = 0, successCalls = 0, failureCalls = 0;
            for (Map<String, Object> m : metrics.values()) {
                totalCalls += ((Number) m.getOrDefault("totalCalls", 0)).longValue();
                successCalls += ((Number) m.getOrDefault("successCalls", 0)).longValue();
                failureCalls += ((Number) m.getOrDefault("failureCalls", 0)).longValue();
            }
            
            // Add QPS to each service
            for (Map.Entry<String, Map<String, Object>> entry : metrics.entrySet()) {
                String serviceName = entry.getKey();
                Map<String, Object> data = entry.getValue();
                double totalQps = 0;
                for (Map.Entry<String, Double> qpsEntry : qpsMap.entrySet()) {
                    if (qpsEntry.getKey().startsWith(serviceName + ":")) {
                        totalQps += qpsEntry.getValue();
                    }
                }
                data.put("qps", totalQps);
            }
            
            double successRate = totalCalls > 0 ? (double) successCalls / totalCalls : 0.0;
            
            result.put("success", true);
            result.put("data", metrics);
            result.put("globalQps", tracer.getGlobalQPS());
            result.put("totalCalls", totalCalls);
            result.put("successCalls", successCalls);
            result.put("failureCalls", failureCalls);
            result.put("successRate", successRate);
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
            Map<String, TraceCollector.ServiceMetrics> allMetrics = tracer.getAllMetrics();
            Map<String, Map<String, Object>> methodMetrics = new HashMap<>();
            
            long totalCalls = 0, successCalls = 0, failureCalls = 0;
            double totalLatency = 0, maxLatency = 0;
            
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
                    methodMetrics.put(methodName, methodData);
                    
                    totalCalls += m.getTotalCalls();
                    successCalls += m.getSuccessCalls();
                    failureCalls += m.getFailureCalls();
                    totalLatency += m.getAvgLatency() * m.getTotalCalls();
                    maxLatency = Math.max(maxLatency, m.getMaxLatency());
                }
            }
            
            Map<String, Object> aggregated = new HashMap<>();
            aggregated.put("totalCalls", totalCalls);
            aggregated.put("successCalls", successCalls);
            aggregated.put("failureCalls", failureCalls);
            aggregated.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls : 0.0);
            aggregated.put("avgLatency", totalCalls > 0 ? totalLatency / totalCalls : 0);
            aggregated.put("maxLatency", maxLatency);
            
            result.put("success", true);
            result.put("serviceName", serviceName);
            result.put("aggregatedMetrics", aggregated);
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
     * Get recent call traces.
     */
    @RequestMapping(value = "/traces/recent", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getRecentTraces(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<TraceCollector.Span> spans = tracer.getRecentSpans(limit);
            
            List<Map<String, Object>> traceData = new ArrayList<>();
            for (TraceCollector.Span span : spans) {
                Map<String, Object> t = new HashMap<>();
                t.put("traceId", span.traceId);
                t.put("serviceName", span.serviceName);
                t.put("method", span.methodName);
                t.put("latency", span.durationMs);
                t.put("success", span.success);
                t.put("timestamp", span.startTime);
                traceData.add(t);
            }
            
            result.put("success", true);
            result.put("data", traceData);
            result.put("total", traceData.size());
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
     * GET /api/circuit-breakers
     * Get all circuit breaker states.
     */
    @RequestMapping(value = "/circuit-breakers", method = RequestMethod.GET)
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
     * POST /api/circuit-breaker/{name}/reset
     * Reset a circuit breaker.
     */
    @RequestMapping(value = "/circuit-breaker/{name}/reset", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> resetCircuitBreaker(@PathVariable("name") String name) {
        Map<String, Object> result = new HashMap<>();
        try {
            CircuitBreaker cb = circuitBreakerRegistry.get(name);
            if (cb == null) {
                result.put("success", false);
                result.put("message", "Circuit breaker not found: " + name);
                return result;
            }
            cb.reset();
            result.put("success", true);
            result.put("message", "Circuit breaker reset: " + name);
            result.put("data", cb.getStats());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    /**
     * POST /api/circuit-breaker/{name}/config
     * Configure a circuit breaker.
     */
    @RequestMapping(value = "/circuit-breaker/{name}/config", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> configCircuitBreaker(
            @PathVariable("name") String name,
            @RequestBody Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            int failureThreshold = ((Number) config.getOrDefault("failureThreshold", 5)).intValue();
            long recoveryTimeoutMs = ((Number) config.getOrDefault("recoveryTimeoutMs", 30000L)).longValue();
            int halfOpenRequests = ((Number) config.getOrDefault("halfOpenRequests", 3)).intValue();
            
            CircuitBreaker cb = circuitBreakerRegistry.updateConfig(name, failureThreshold, recoveryTimeoutMs, halfOpenRequests);
            result.put("success", true);
            result.put("message", "Configuration updated: " + name);
            result.put("data", cb.getStats());
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    
    // ====================
    // Health APIs
    // ====================
    
    /**
     * GET /api/health
     * System health check.
     */
    @RequestMapping(value = "/health", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
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
            
            // System
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
    
    // ====================
    // Dashboard Summary
    // ====================
    
    /**
     * GET /api/dashboard/summary
     * Dashboard summary data.
     */
    @RequestMapping(value = "/dashboard/summary", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> summary = new HashMap<>();
            
            summary.put("globalQps", tracer.getGlobalQPS());
            
            Map<String, Map<String, Object>> allMetrics = tracer.getAllMetricsSummary();
            long totalCalls = 0, successCalls = 0;
            for (Map<String, Object> m : allMetrics.values()) {
                totalCalls += ((Number) m.getOrDefault("totalCalls", 0)).longValue();
                successCalls += ((Number) m.getOrDefault("successCalls", 0)).longValue();
            }
            
            summary.put("totalCalls", totalCalls);
            summary.put("successCalls", successCalls);
            summary.put("failureCalls", totalCalls - successCalls);
            summary.put("successRate", totalCalls > 0 ? (double) successCalls / totalCalls : 0.0);
            
            summary.put("circuitBreakerStates", circuitBreakerRegistry.getStateCounts());
            summary.put("totalCircuitBreakers", circuitBreakerRegistry.size());
            
            result.put("success", true);
            result.put("data", summary);
            result.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
