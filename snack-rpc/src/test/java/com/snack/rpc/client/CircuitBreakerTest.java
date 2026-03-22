package com.snack.rpc.client;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CircuitBreaker.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class CircuitBreakerTest {

    @Test
    public void testInitialStateIsClosed() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000, 2);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue("Should allow request initially", cb.allowRequest());
    }
    
    @Test
    public void testOpensAfterThreshold() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000, 2);
        
        // Record failures up to threshold
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        
        cb.recordFailure(); // 3rd failure
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse("Should not allow request when open", cb.allowRequest());
    }
    
    @Test
    public void testHalfOpenAfterTimeout() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(3, 100, 2);
        
        // Open the circuit
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        
        // Wait for recovery timeout
        Thread.sleep(150);
        
        assertTrue("Should allow request after timeout", cb.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
    }
    
    @Test
    public void testSuccessInHalfOpenClosesCircuit() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(3, 50, 2);
        
        // Open then transition to half-open
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        Thread.sleep(100);
        cb.allowRequest(); // enters half-open
        
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }
    
    @Test
    public void testFailureInHalfOpenReopensCircuit() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(3, 50, 2);
        
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        Thread.sleep(100);
        cb.allowRequest(); // half-open
        cb.recordFailure();
        
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }
    
    @Test
    public void testHalfOpenMaxCalls() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(3, 50, 2);
        
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        Thread.sleep(100);
        
        // Transition to half-open (doesn't count as a half-open call)
        assertTrue(cb.allowRequest());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
        
        // First two calls allowed in half-open
        assertTrue(cb.allowRequest());
        assertTrue(cb.allowRequest());
        // Third should be blocked
        assertFalse(cb.allowRequest());
    }
    
    @Test
    public void testSuccessResetsFailureCount() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(5, 50, 2);
        
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        // Threshold is 5, so after 3 failures still CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        
        // Add 2 more failures to open
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        
        // Wait to enter half-open
        Thread.sleep(60);
        cb.allowRequest(); // enters HALF_OPEN
        
        cb.recordSuccess(); // CLOSES circuit
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }
    
    @Test
    public void testForceState() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000, 2);
        
        cb.forceState(CircuitBreaker.State.OPEN);
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        
        cb.forceState(CircuitBreaker.State.CLOSED);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }
    
    @Test
    public void testGetStats() {
        CircuitBreaker cb = new CircuitBreaker(5, 1000, 2);
        cb.recordFailure();
        cb.recordFailure();
        
        CircuitBreaker.CircuitBreakerStats stats = cb.getStats();
        assertEquals(CircuitBreaker.State.CLOSED, stats.state);
        assertEquals(2, stats.failureCount);
    }
}
