package com.snack.rpc.client;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for RetryPolicy.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RetryPolicyTest {

    @Test
    public void testFixedStrategy() {
        RetryPolicy policy = new RetryPolicy(
                RetryPolicy.Strategy.FIXED, 5, 100, 1000, 0.0);
        
        assertTrue("Should retry attempt 1", policy.shouldRetry(1));
        assertTrue("Should retry attempt 2", policy.shouldRetry(2));
        assertTrue("Should retry attempt 4", policy.shouldRetry(4));
        assertFalse("Should not retry at max", policy.shouldRetry(5));
        
        // Fixed: all delays should be same
        assertEquals(100, policy.getDelayMs(2));
        assertEquals(100, policy.getDelayMs(3));
    }
    
    @Test
    public void testExponentialStrategy() {
        RetryPolicy policy = new RetryPolicy(
                RetryPolicy.Strategy.EXPONENTIAL, 5, 100, 10000, 0.0);
        
        // Exponential: 100, 200, 400, 800...
        assertEquals(100, policy.getDelayMs(2));  // 100 * 2^0
        assertEquals(200, policy.getDelayMs(3));   // 100 * 2^1
        assertEquals(400, policy.getDelayMs(4));    // 100 * 2^2
    }
    
    @Test
    public void testMaxDelayCap() {
        RetryPolicy policy = new RetryPolicy(
                RetryPolicy.Strategy.EXPONENTIAL, 10, 100, 500, 0.0);
        
        // Should be capped at 500
        assertEquals(500, policy.getDelayMs(10));  // would be 100 * 2^8 = 25600, but capped
    }
    
    @Test
    public void testJitter() {
        RetryPolicy policy = new RetryPolicy(
                RetryPolicy.Strategy.FIXED, 5, 1000, 2000, 0.5);
        
        // With jitter, delays should vary
        long delay1 = policy.getDelayMs(2);
        long delay2 = policy.getDelayMs(2);
        
        // Both should be around 1000, but different due to jitter
        assertTrue("Delay should be >= 500 (1000 - 50%)", delay1 >= 500);
        assertTrue("Delay should be <= 1500 (1000 + 50%)", delay1 <= 1500);
    }
    
    @Test
    public void testRetryableExceptions() {
        RetryPolicy policy = RetryPolicy.fast();
        
        assertTrue("Timeout is retryable", 
                policy.isRetryable(new RpcInvoker.RpcTimeoutException("timeout")));
        assertTrue("ConnectException is retryable", 
                policy.isRetryable(new java.net.ConnectException("connection refused")));
        assertTrue("SocketTimeoutException is retryable", 
                policy.isRetryable(new java.net.SocketTimeoutException("timeout")));
    }
    
    @Test
    public void testFastPreset() {
        RetryPolicy policy = RetryPolicy.fast();
        assertEquals(3, policy.getMaxAttempts());
        assertEquals(RetryPolicy.Strategy.FIXED, policy.getStrategy());
    }
    
    @Test
    public void testReliablePreset() {
        RetryPolicy policy = RetryPolicy.reliable();
        assertEquals(5, policy.getMaxAttempts());
        assertEquals(RetryPolicy.Strategy.EXPONENTIAL, policy.getStrategy());
    }
    
    @Test
    public void testNonePreset() {
        RetryPolicy policy = RetryPolicy.none();
        assertEquals(1, policy.getMaxAttempts());
    }
}
