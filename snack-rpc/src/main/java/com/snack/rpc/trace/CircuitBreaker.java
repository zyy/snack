package com.snack.rpc.trace;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit Breaker implementation for RPC calls.
 * States: CLOSED (normal), OPEN (failing), HALF_OPEN (testing recovery)
 * 
 * Features:
 * - Configurable failure threshold
 * - Configurable break duration
 * - Half-open recovery trial count
 * 
 * Created by yangyang.zhao on 2026/3/23.
 */
public class CircuitBreaker {
    
    public enum State {
        CLOSED,  // Normal operation
        OPEN,    // Circuit is open, requests fail fast
        HALF_OPEN // Testing if service recovered
    }
    
    private final String name;
    private final int failureThreshold;       // Number of failures to open circuit
    private final long breakDurationMs;       // How long to stay open
    private final int halfOpenMaxTrials;      // Successful trials to close circuit
    
    private volatile State state = State.CLOSED;
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private volatile long lastFailureTime = 0;
    private volatile long openedAt = 0;
    
    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong blockedRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    
    public CircuitBreaker(String name) {
        this(name, 5, 30000, 3); // defaults
    }
    
    public CircuitBreaker(String name, int failureThreshold, long breakDurationMs, int halfOpenMaxTrials) {
        this.name = name;
        this.failureThreshold = failureThreshold > 0 ? failureThreshold : 5;
        this.breakDurationMs = breakDurationMs > 0 ? breakDurationMs : 30000;
        this.halfOpenMaxTrials = halfOpenMaxTrials > 0 ? halfOpenMaxTrials : 3;
    }
    
    /**
     * Check if request is allowed through the circuit breaker.
     * Returns true if request should proceed, false if blocked.
     */
    public boolean allowRequest() {
        totalRequests.incrementAndGet();
        
        switch (state) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (System.currentTimeMillis() - openedAt >= breakDurationMs) {
                    // Transition to HALF_OPEN
                    state = State.HALF_OPEN;
                    successCount.set(0);
                    failureCount.set(0);
                    return true;
                }
                blockedRequests.incrementAndGet();
                return false;
                
            case HALF_OPEN:
                // Allow limited requests in half-open state
                return true;
                
            default:
                return true;
        }
    }
    
    /**
     * Record a successful request.
     */
    public void recordSuccess() {
        successfulRequests.incrementAndGet();
        
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= halfOpenMaxTrials) {
                // Transition to CLOSED
                state = State.CLOSED;
                failureCount.set(0);
                successCount.set(0);
            }
        } else if (state == State.CLOSED) {
            // Reset failure count on success
            failureCount.set(0);
        }
    }
    
    /**
     * Record a failed request.
     */
    public void recordFailure() {
        failedRequests.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        
        if (state == State.HALF_OPEN) {
            // Immediate transition back to OPEN
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
            successCount.set(0);
        } else if (state == State.CLOSED) {
            if (failureCount.incrementAndGet() >= failureThreshold) {
                // Open the circuit
                state = State.OPEN;
                openedAt = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Get circuit breaker state.
     */
    public State getState() {
        if (state == State.OPEN && System.currentTimeMillis() - openedAt >= breakDurationMs) {
            return State.HALF_OPEN; // About to transition
        }
        return state;
    }
    
    /**
     * Get time remaining until circuit may attempt recovery.
     */
    public long getTimeUntilRetry() {
        if (state != State.OPEN) return 0;
        long elapsed = System.currentTimeMillis() - openedAt;
        return Math.max(0, breakDurationMs - elapsed);
    }
    
    /**
     * Manually reset the circuit breaker.
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
    }
    
    /**
     * Manually force the circuit breaker open.
     */
    public void forceOpen() {
        state = State.OPEN;
        openedAt = System.currentTimeMillis();
    }
    
    /**
     * Get statistics for monitoring.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("name", name);
        stats.put("state", getState().name());
        stats.put("failureThreshold", failureThreshold);
        stats.put("breakDurationMs", breakDurationMs);
        stats.put("halfOpenMaxTrials", halfOpenMaxTrials);
        stats.put("currentFailureCount", failureCount.get());
        stats.put("currentSuccessCount", successCount.get());
        stats.put("totalRequests", totalRequests.get());
        stats.put("blockedRequests", blockedRequests.get());
        stats.put("successfulRequests", successfulRequests.get());
        stats.put("failedRequests", failedRequests.get());
        stats.put("lastFailureTime", lastFailureTime);
        stats.put("timeUntilRetry", getTimeUntilRetry());
        return stats;
    }
    
    // Getters for configuration
    public String getName() { return name; }
    public int getFailureThreshold() { return failureThreshold; }
    public long getBreakDurationMs() { return breakDurationMs; }
    public int getHalfOpenMaxTrials() { return halfOpenMaxTrials; }
}
