package com.snack.rpc.client;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for RateLimiter.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RateLimiterTest {

    @Test
    public void testBurstCapacity() {
        RateLimiter limiter = new RateLimiter(10, 5); // 10/s, burst=5
        
        // Should allow burst of 5 immediately
        assertTrue("Should acquire 1", limiter.tryAcquire(1));
        assertTrue("Should acquire 2", limiter.tryAcquire(1));
        assertTrue("Should acquire 3", limiter.tryAcquire(1));
        assertTrue("Should acquire 4", limiter.tryAcquire(1));
        assertTrue("Should acquire 5", limiter.tryAcquire(1));
    }
    
    @Test
    public void testExhaustBurst() {
        RateLimiter limiter = new RateLimiter(1000, 3); // high rate, small burst
        
        // Exhaust burst
        assertTrue(limiter.tryAcquire(1));
        assertTrue(limiter.tryAcquire(1));
        assertTrue(limiter.tryAcquire(1));
        
        // Should be exhausted
        assertFalse("Should not acquire when exhausted", limiter.tryAcquire(1));
    }
    
    @Test
    public void testTokenRefill() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1000, 5); // 1000/s = ~1/ms
        
        // Exhaust all
        assertTrue(limiter.tryAcquire(5));
        
        // Wait for some refill
        Thread.sleep(10); // should get ~10 tokens
        
        double available = limiter.getAvailableTokens();
        assertTrue("Should have refilled tokens: " + available, available > 0);
    }
    
    @Test
    public void testAcquireWithTimeout() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1000, 1); // 1 token
        
        assertTrue(limiter.tryAcquire(1)); // consume the only token
        
        // Should timeout
        boolean acquired = limiter.acquire(1, 5, TimeUnit.MILLISECONDS);
        assertFalse("Should timeout when no tokens", acquired);
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        final RateLimiter limiter = new RateLimiter(10000, 100);
        final AtomicInteger successCount = new AtomicInteger(0);
        
        // Try 100 concurrent acquisitions
        Thread[] threads = new Thread[100];
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                if (limiter.tryAcquire(1)) {
                    successCount.incrementAndGet();
                }
            });
        }
        
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        
        // Should succeed for at least some (burst capacity + refill)
        assertTrue("At least some should succeed", successCount.get() > 0);
    }
    
    @Test
    public void testGetRate() {
        RateLimiter limiter = new RateLimiter(50, 10);
        assertEquals(50, limiter.getRate(), 0.001);
    }
}
