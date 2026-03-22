package com.snack.rpc.client;

/**
 * Simple circuit breaker implementation.
 * States: CLOSED -> OPEN -> HALF_OPEN -> CLOSED
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class CircuitBreaker {
    
    public enum State {
        CLOSED,   // Normal operation, requests pass through
        OPEN,     // Circuit is tripped, requests fail fast
        HALF_OPEN // Testing if the service recovered
    }
    
    // Configuration
    private final int failureThreshold;      // failures before opening circuit
    private final long recoveryTimeout;     // ms to wait before trying again
    private final int halfOpenMaxCalls;      // max calls in half-open state
    
    // State
    private volatile State state = State.CLOSED;
    private int failureCount = 0;
    private int halfOpenCallCount = 0;
    private long lastFailureTime = 0;
    
    public CircuitBreaker(int failureThreshold, long recoveryTimeoutMs, int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeout = recoveryTimeoutMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }
    
    /**
     * Check if a call is allowed through the circuit breaker.
     */
    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Check if recovery timeout has passed
                if (System.currentTimeMillis() - lastFailureTime >= recoveryTimeout) {
                    state = State.HALF_OPEN;
                    halfOpenCallCount = 0;
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited calls to test recovery
                if (halfOpenCallCount < halfOpenMaxCalls) {
                    halfOpenCallCount++;
                    return true;
                }
                return false;
                
            default:
                return true;
        }
    }
    
    /**
     * Record a successful call.
     */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            // Success in half-open state -> close circuit
            state = State.CLOSED;
            failureCount = 0;
            halfOpenCallCount = 0;
        } else if (state == State.CLOSED) {
            // Reset failure count on success
            failureCount = Math.max(0, failureCount - 1);
        }
    }
    
    /**
     * Record a failed call.
     */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        failureCount++;
        
        if (state == State.HALF_OPEN) {
            // Any failure in half-open -> re-open circuit
            state = State.OPEN;
            halfOpenCallCount = 0;
        } else if (state == State.CLOSED) {
            if (failureCount >= failureThreshold) {
                state = State.OPEN;
            }
        }
    }
    
    /**
     * Force the circuit breaker to a specific state.
     */
    public void forceState(State newState) {
        this.state = newState;
        if (newState == State.CLOSED) {
            failureCount = 0;
            halfOpenCallCount = 0;
        } else if (newState == State.HALF_OPEN) {
            halfOpenCallCount = 0;
        }
    }
    
    public State getState() {
        return state;
    }
    
    public int getFailureCount() {
        return failureCount;
    }
    
    /**
     * Get circuit breaker statistics.
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(state, failureCount, halfOpenCallCount, lastFailureTime);
    }
    
    public static class CircuitBreakerStats {
        public final State state;
        public final int failureCount;
        public final int halfOpenCallCount;
        public final long lastFailureTime;
        
        public CircuitBreakerStats(State state, int failureCount, int halfOpenCallCount, long lastFailureTime) {
            this.state = state;
            this.failureCount = failureCount;
            this.halfOpenCallCount = halfOpenCallCount;
            this.lastFailureTime = lastFailureTime;
        }
        
        @Override
        public String toString() {
            return String.format("CircuitBreaker[state=%s, failures=%d, halfOpenCalls=%d]", 
                    state, failureCount, halfOpenCallCount);
        }
    }
}
