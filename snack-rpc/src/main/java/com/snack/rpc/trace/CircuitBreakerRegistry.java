package com.snack.rpc.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and manager for all CircuitBreaker instances.
 * Provides centralized access to circuit breaker state and configuration.
 * 
 * Created by yangyang.zhao on 2026/3/23.
 */
public class CircuitBreakerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerRegistry.class);
    
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // Default configuration
    private int defaultFailureThreshold = 5;
    private long defaultBreakDurationMs = 30000;
    private int defaultHalfOpenMaxTrials = 3;
    
    private static volatile CircuitBreakerRegistry INSTANCE = null;
    
    public static CircuitBreakerRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (CircuitBreakerRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CircuitBreakerRegistry();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Get or create a circuit breaker for the given service.
     */
    public CircuitBreaker getOrCreate(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> new CircuitBreaker(name, defaultFailureThreshold, 
                                       defaultBreakDurationMs, defaultHalfOpenMaxTrials));
    }
    
    /**
     * Get or create a circuit breaker with custom configuration.
     */
    public CircuitBreaker getOrCreate(String serviceName, int failureThreshold, 
                                        long breakDurationMs, int halfOpenMaxTrials) {
        return circuitBreakers.computeIfAbsent(serviceName, 
            name -> new CircuitBreaker(name, failureThreshold, breakDurationMs, halfOpenMaxTrials));
    }
    
    /**
     * Get a circuit breaker if it exists.
     */
    public CircuitBreaker get(String serviceName) {
        return circuitBreakers.get(serviceName);
    }
    
    /**
     * Check if a circuit breaker exists for the service.
     */
    public boolean hasCircuitBreaker(String serviceName) {
        return circuitBreakers.containsKey(serviceName);
    }
    
    /**
     * Remove a circuit breaker.
     */
    public CircuitBreaker remove(String serviceName) {
        return circuitBreakers.remove(serviceName);
    }
    
    /**
     * Get all circuit breaker states.
     */
    public List<Map<String, Object>> getAllStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CircuitBreaker cb : circuitBreakers.values()) {
            result.add(cb.getStats());
        }
        return result;
    }
    
    /**
     * Get circuit breaker statistics as a map keyed by service name.
     */
    public Map<String, Map<String, Object>> getStatsMap() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, CircuitBreaker> entry : circuitBreakers.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getStats());
        }
        return result;
    }
    
    /**
     * Update configuration for a specific circuit breaker.
     */
    public CircuitBreaker updateConfig(String serviceName, int failureThreshold,
                                         long breakDurationMs, int halfOpenMaxTrials) {
        CircuitBreaker existing = circuitBreakers.get(serviceName);
        if (existing != null) {
            // Remove old one and create new with updated config
            circuitBreakers.remove(serviceName);
            return getOrCreate(serviceName, failureThreshold, breakDurationMs, halfOpenMaxTrials);
        }
        return getOrCreate(serviceName, failureThreshold, breakDurationMs, halfOpenMaxTrials);
    }
    
    /**
     * Reset all circuit breakers to closed state.
     */
    public void resetAll() {
        for (CircuitBreaker cb : circuitBreakers.values()) {
            cb.reset();
        }
        logger.info("All circuit breakers reset");
    }
    
    /**
     * Get count of circuit breakers in each state.
     */
    public Map<String, Integer> getStateCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("CLOSED", 0);
        counts.put("OPEN", 0);
        counts.put("HALF_OPEN", 0);
        
        for (CircuitBreaker cb : circuitBreakers.values()) {
            String state = cb.getState().name();
            counts.put(state, counts.get(state) + 1);
        }
        return counts;
    }
    
    /**
     * Get total number of circuit breakers.
     */
    public int size() {
        return circuitBreakers.size();
    }
    
    // Setters for default configuration
    public void setDefaultFailureThreshold(int threshold) {
        this.defaultFailureThreshold = threshold;
    }
    
    public void setDefaultBreakDurationMs(long durationMs) {
        this.defaultBreakDurationMs = durationMs;
    }
    
    public void setDefaultHalfOpenMaxTrials(int trials) {
        this.defaultHalfOpenMaxTrials = trials;
    }
    
    // Getters for default configuration
    public int getDefaultFailureThreshold() { return defaultFailureThreshold; }
    public long getDefaultBreakDurationMs() { return defaultBreakDurationMs; }
    public int getDefaultHalfOpenMaxTrials() { return defaultHalfOpenMaxTrials; }
}
