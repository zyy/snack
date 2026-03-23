package com.snack.rpc.trace;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Rate Limiter implementation.
 * 
 * Features:
 * - Configurable QPS (queries per second) limit
 * - Token bucket algorithm for smooth rate limiting
 * - Per-service rate limiting support
 * 
 * Created by yangyang.zhao on 2026/3/23.
 */
public class RateLimiter {
    
    private final String name;
    private final double qpsLimit;          // Maximum QPS allowed
    private final double bucketCapacity;    // Maximum tokens in bucket
    private volatile double currentTokens;
    private volatile long lastRefillTime;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);
    
    public RateLimiter(String name, double qpsLimit) {
        this.name = name;
        this.qpsLimit = qpsLimit > 0 ? qpsLimit : 1.0;
        this.bucketCapacity = qpsLimit * 2; // Allow burst of up to 2x QPS
        this.currentTokens = this.bucketCapacity;
        this.lastRefillTime = System.currentTimeMillis();
    }
    
    /**
     * Try to acquire a token. Returns true if request is allowed, false if rate limited.
     */
    public synchronized boolean tryAcquire() {
        totalRequests.incrementAndGet();
        
        refillTokens();
        
        if (currentTokens >= 1) {
            currentTokens -= 1;
            allowedRequests.incrementAndGet();
            return true;
        }
        
        rejectedRequests.incrementAndGet();
        return false;
    }
    
    /**
     * Refill tokens based on elapsed time.
     */
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTime;
        
        if (elapsed > 0) {
            // Add tokens based on QPS rate
            double tokensToAdd = (elapsed / 1000.0) * qpsLimit;
            currentTokens = Math.min(bucketCapacity, currentTokens + tokensToAdd);
            lastRefillTime = now;
        }
    }
    
    /**
     * Get current token availability ratio (0.0 to 1.0).
     */
    public double getTokenRatio() {
        return currentTokens / bucketCapacity;
    }
    
    /**
     * Check if rate limiter is healthy (not constantly rejecting).
     */
    public boolean isHealthy() {
        long total = totalRequests.get();
        if (total == 0) return true;
        double rejectionRate = (double) rejectedRequests.get() / total;
        return rejectionRate < 0.5; // Not healthy if >50% rejection rate
    }
    
    /**
     * Get statistics for monitoring.
     */
    public Stats getStats() {
        return new Stats(
            name,
            qpsLimit,
            currentTokens,
            bucketCapacity,
            totalRequests.get(),
            allowedRequests.get(),
            rejectedRequests.get(),
            totalRequests.get() > 0 
                ? (double) rejectedRequests.get() / totalRequests.get() * 100 
                : 0
        );
    }
    
    // Getters
    public String getName() { return name; }
    public double getQpsLimit() { return qpsLimit; }
    public long getTotalRequests() { return totalRequests.get(); }
    public long getAllowedRequests() { return allowedRequests.get(); }
    public long getRejectedRequests() { return rejectedRequests.get(); }
    
    /**
     * Statistics record.
     */
    public static class Stats {
        public final String name;
        public final double qpsLimit;
        public final double currentTokens;
        public final double bucketCapacity;
        public final long totalRequests;
        public final long allowedRequests;
        public final long rejectedRequests;
        public final double rejectionRate;
        
        public Stats(String name, double qpsLimit, double currentTokens, 
                     double bucketCapacity, long totalRequests, 
                     long allowedRequests, long rejectedRequests, double rejectionRate) {
            this.name = name;
            this.qpsLimit = qpsLimit;
            this.currentTokens = currentTokens;
            this.bucketCapacity = bucketCapacity;
            this.totalRequests = totalRequests;
            this.allowedRequests = allowedRequests;
            this.rejectedRequests = rejectedRequests;
            this.rejectionRate = rejectionRate;
        }
    }
    
    /**
     * Registry for managing multiple rate limiters.
     */
    public static class Registry {
        private static final Registry INSTANCE = new Registry();
        
        private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
        
        public static Registry getInstance() {
            return INSTANCE;
        }
        
        /**
         * Get or create a rate limiter for a service.
         */
        public RateLimiter getOrCreate(String name, double qpsLimit) {
            return limiters.computeIfAbsent(name, k -> new RateLimiter(name, qpsLimit));
        }
        
        /**
         * Try to acquire permit for a service.
         */
        public boolean tryAcquire(String name, double qpsLimit) {
            return getOrCreate(name, qpsLimit).tryAcquire();
        }
        
        /**
         * Get all rate limiter statistics.
         */
        public java.util.List<Stats> getAllStats() {
            java.util.List<Stats> result = new java.util.ArrayList<>();
            for (RateLimiter limiter : limiters.values()) {
                result.add(limiter.getStats());
            }
            return result;
        }
        
        /**
         * Remove a rate limiter.
         */
        public RateLimiter remove(String name) {
            return limiters.remove(name);
        }
        
        /**
         * Get count of rate limiters.
         */
        public int size() {
            return limiters.size();
        }
    }
}
