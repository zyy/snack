package com.snack.rpc.client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter.
 * Allows up to 'rate' requests per second, with burst support.
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RateLimiter {
    
    private final double rate;           // tokens per second
    private final int maxTokens;        // bucket capacity
    private final AtomicLong tokens;
    private final AtomicLong lastRefillTime;
    private final double refillAmount;
    
    /**
     * Create a rate limiter.
     * 
     * @param rate tokens per second
     * @param burst maximum burst size (number of concurrent requests allowed)
     */
    public RateLimiter(double rate, int burst) {
        this.rate = rate;
        this.maxTokens = burst;
        this.tokens = new AtomicLong(burst);
        this.lastRefillTime = new AtomicLong(System.nanoTime());
        this.refillAmount = rate / 1_000_000_000.0; // per nanosecond
    }
    
    /**
     * Try to acquire a token.
     * 
     * @param permits number of permits needed
     * @return true if token was acquired, false otherwise
     */
    public boolean tryAcquire(int permits) {
        refill();
        
        long current = tokens.get();
        long desired = current - permits;
        
        while (desired >= 0) {
            if (tokens.compareAndSet(current, desired)) {
                return true;
            }
            current = tokens.get();
            desired = current - permits;
        }
        return false;
    }
    
    /**
     * Acquire a token, waiting if necessary.
     * 
     * @param permits number of permits needed
     * @param timeout max time to wait
     * @param unit time unit
     * @return true if acquired, false if timeout
     */
    public boolean acquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        
        while (true) {
            if (tryAcquire(permits)) {
                return true;
            }
            
            long waitTime = deadline - System.nanoTime();
            if (waitTime <= 0) {
                return false;
            }
            
            // Wait a bit and retry
            Thread.sleep(Math.min(waitTime, 10_000_000), 0); // max 10ms
        }
    }
    
    /**
     * Refill tokens based on elapsed time.
     */
    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTime.get();
        long elapsed = now - last;
        
        if (elapsed > 0 && lastRefillTime.compareAndSet(last, now)) {
            double tokensToAdd = elapsed * refillAmount;
            long current = tokens.get();
            long desired = Math.min(maxTokens, current + (long) tokensToAdd);
            tokens.set((int) desired);
        }
    }
    
    /**
     * Get current number of available tokens.
     */
    public double getAvailableTokens() {
        refill();
        return tokens.get();
    }
    
    /**
     * Get the rate limit.
     */
    public double getRate() {
        return rate;
    }
    
    @Override
    public String toString() {
        return String.format("RateLimiter[rate=%.1f/s, available=%.1f]", rate, getAvailableTokens());
    }
}
