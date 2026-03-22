package com.snack.admin.controller;

import com.snack.rpc.trace.TraceCollector;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for tracing and metrics visualization.
 * Created by yangyang.zhao on 2026/3/22.
 */
@Controller
@RequestMapping("/trace")
public class TraceController {
    
    private final TraceCollector tracer = TraceCollector.getInstance();
    
    /**
     * List recent traces.
     */
    @RequestMapping("/list")
    @ResponseBody
    public Map<String, Object> listTraces(
            @RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> result = new HashMap<>();
        List<TraceCollector.Span> spans = tracer.getRecentSpans(limit);
        result.put("success", true);
        result.put("data", spans);
        result.put("total", spans.size());
        return result;
    }
    
    /**
     * Get details of a specific trace.
     */
    @RequestMapping("/detail")
    @ResponseBody
    public Map<String, Object> traceDetail(
            @RequestParam String traceId) {
        Map<String, Object> result = new HashMap<>();
        List<TraceCollector.Span> spans = tracer.getTrace(traceId);
        result.put("success", true);
        result.put("data", spans);
        result.put("total", spans.size());
        return result;
    }
    
    /**
     * Get metrics for all services.
     */
    @RequestMapping("/metrics")
    @ResponseBody
    public Map<String, Object> allMetrics() {
        Map<String, Object> result = new HashMap<>();
        Map<String, TraceCollector.ServiceMetrics> metrics = tracer.getAllMetrics();
        Map<String, Object> formatted = new HashMap<>();
        for (Map.Entry<String, TraceCollector.ServiceMetrics> entry : metrics.entrySet()) {
            TraceCollector.ServiceMetrics m = entry.getValue();
            Map<String, Object> stat = new HashMap<>();
            stat.put("totalCalls", m.getTotalCalls());
            stat.put("successCalls", m.getSuccessCalls());
            stat.put("failureCalls", m.getFailureCalls());
            stat.put("successRate", m.getSuccessRate());
            stat.put("avgLatency", m.getAvgLatency());
            stat.put("minLatency", m.getMinLatency());
            stat.put("maxLatency", m.getMaxLatency());
            stat.put("p50", m.getP50());
            stat.put("p90", m.getP90());
            stat.put("p99", m.getP99());
            formatted.put(entry.getKey(), stat);
        }
        result.put("success", true);
        result.put("data", formatted);
        return result;
    }
    
    /**
     * Get metrics for a specific service method.
     */
    @RequestMapping("/metrics/service")
    @ResponseBody
    public Map<String, Object> serviceMetrics(
            @RequestParam String service,
            @RequestParam(required = false) String method) {
        Map<String, Object> result = new HashMap<>();
        String key = method != null ? service + ":" + method : service;
        TraceCollector.ServiceMetrics m = tracer.getMetrics(service, method);
        if (m == null) {
            result.put("success", false);
            result.put("message", "Metrics not found for " + key);
            return result;
        }
        Map<String, Object> stat = new HashMap<>();
        stat.put("totalCalls", m.getTotalCalls());
        stat.put("successCalls", m.getSuccessCalls());
        stat.put("failureCalls", m.getFailureCalls());
        stat.put("successRate", m.getSuccessRate());
        stat.put("avgLatency", m.getAvgLatency());
        stat.put("minLatency", m.getMinLatency());
        stat.put("maxLatency", m.getMaxLatency());
        stat.put("p50", m.getP50());
        stat.put("p90", m.getP90());
        stat.put("p99", m.getP99());
        result.put("success", true);
        result.put("data", stat);
        return result;
    }
    
    /**
     * Reset all tracing data.
     */
    @RequestMapping("/reset")
    @ResponseBody
    public Map<String, Object> reset() {
        tracer.reset();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "All tracing data cleared");
        return result;
    }
}