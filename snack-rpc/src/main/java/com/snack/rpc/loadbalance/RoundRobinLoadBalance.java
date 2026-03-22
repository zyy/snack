package com.snack.rpc.loadbalance;

import com.snack.rpc.spi.LoadBalanceSPI;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancer implementation.
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public class RoundRobinLoadBalance implements LoadBalanceSPI {
    
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public String getName() {
        return "roundrobin";
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority as default
    }
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        // Simple round-robin selection
        int index = Math.abs(counter.getAndIncrement() % addresses.size());
        return addresses.get(index);
    }
    
    @Override
    public void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses) {
        // Remove failed address from list
        if (addresses != null) {
            addresses.remove(failed);
        }
    }
    
    @Override
    public void onSuccess(InetSocketAddress succeeded, List<InetSocketAddress> addresses) {
        // Nothing to do for round-robin
    }
    
    @Override
    public void updateWeight(InetSocketAddress address, int weight) {
        // Round-robin doesn't support weights
    }
    
    @Override
    public void initialize(Properties config) {
        // No configuration needed
    }
}