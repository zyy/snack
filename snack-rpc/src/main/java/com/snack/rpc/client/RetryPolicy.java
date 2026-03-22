package com.snack.rpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Configurable retry policy for RPC calls.
 * Supports multiple strategies: fixed delay, exponential backoff, jitter.
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);
    
    public enum Strategy {
        FIXED,        // Fixed delay between retries
        EXPONENTIAL,   // Exponential backoff: delay * 2^n
        FIBONACCI,    // Fibonacci backoff: delay * fib(n)
        JITTER        // Random jitter added to any strategy
    }
    
    // Configuration
    private final Strategy strategy;
    private final int maxAttempts;         // max total attempts (including first)
    private final long baseDelayMs;       // base delay in milliseconds
    private final long maxDelayMs;        // max delay cap
    private final double jitterFactor;    // 0.0 to 1.0, fraction of delay to randomize
    private final Random random = new Random();
    
    public RetryPolicy(Strategy strategy, int maxAttempts, long baseDelayMs, long maxDelayMs, double jitterFactor) {
        this.strategy = strategy;
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = Math.max(0, Math.min(1, jitterFactor));
    }
    
    /**
     * Create a retry policy from config.
     */
    public static RetryPolicy fromConfig(int maxAttempts, long baseDelayMs, long maxDelayMs, 
            String strategyName, double jitterFactor) {
        Strategy strategy = Strategy.FIXED;
        if (strategyName != null) {
            try {
                strategy = Strategy.valueOf(strategyName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown retry strategy '{}', using FIXED", strategyName);
            }
        }
        return new RetryPolicy(strategy, maxAttempts, baseDelayMs, maxDelayMs, jitterFactor);
    }
    
    /**
     * Check if we should retry.
     * 
     * @param attempt current attempt number (1-based, so attempt=1 means first try)
     * @return true if should retry, false if max attempts reached
     */
    public boolean shouldRetry(int attempt) {
        return attempt < maxAttempts;
    }
    
    /**
     * Get the delay before the next retry.
     * 
     * @param attempt current attempt number (1-based)
     * @return delay in milliseconds
     */
    public long getDelayMs(int attempt) {
        if (attempt <= 1) {
            return 0; // no delay before first retry
        }
        
        long delay = calculateDelay(attempt - 1); // attempt-1 = retry number (0-based)
        
        // Add jitter if configured
        if (jitterFactor > 0) {
            long jitter = (long) (delay * jitterFactor * (random.nextDouble() * 2 - 1));
            delay += jitter;
        }
        
        return Math.max(0, Math.min(delay, maxDelayMs));
    }
    
    private long calculateDelay(int retryAttempt) {
        switch (strategy) {
            case FIXED:
                return baseDelayMs;
                
            case EXPONENTIAL:
                // baseDelay * 2^(retryAttempt-1), so attempt 2=baseDelay, attempt 3=2x, etc.
                long delay = baseDelayMs * (1L << (retryAttempt - 1));
                return delay;
                
            case FIBONACCI:
                return baseDelayMs * fibonacci(retryAttempt);
                
            case JITTER:
                // Pure random between 0 and baseDelay
                return (long) (baseDelayMs * random.nextDouble());
                
            default:
                return baseDelayMs;
        }
    }
    
    private long fibonacci(int n) {
        if (n <= 1) return 1;
        long a = 1, b = 1;
        for (int i = 2; i < n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }
    
    /**
     * Check if an exception is retryable.
     * Default: retry on timeout, connection errors; don't retry on biz errors.
     */
    public boolean isRetryable(Exception e) {
        if (e instanceof RpcInvoker.RpcTimeoutException) {
            return true;
        }
        if (e instanceof java.net.ConnectException) {
            return true;
        }
        if (e instanceof java.net.SocketTimeoutException) {
            return true;
        }
        if (e instanceof java.io.IOException) {
            return true;
        }
        // Don't retry: biz errors (like service not found, method not found)
        return false;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public Strategy getStrategy() {
        return strategy;
    }
    
    @Override
    public String toString() {
        return String.format("RetryPolicy[strategy=%s, maxAttempts=%d, baseDelay=%dms, jitter=%.0f%%]",
                strategy, maxAttempts, baseDelayMs, jitterFactor * 100);
    }
    
    // =====================
    // Common presets
    // =====================
    
    /** Fast retry: 3 attempts, 100ms base delay */
    public static RetryPolicy fast() {
        return new RetryPolicy(Strategy.FIXED, 3, 100, 1000, 0.1);
    }
    
    /** Reliable retry: 5 attempts, 500ms base delay, exponential backoff */
    public static RetryPolicy reliable() {
        return new RetryPolicy(Strategy.EXPONENTIAL, 5, 500, 10000, 0.2);
    }
    
    /** Aggressive retry: 10 attempts, exponential backoff, lots of jitter */
    public static RetryPolicy aggressive() {
        return new RetryPolicy(Strategy.EXPONENTIAL, 10, 100, 30000, 0.3);
    }
    
    /** No retry */
    public static RetryPolicy none() {
        return new RetryPolicy(Strategy.FIXED, 1, 0, 0, 0);
    }
}
