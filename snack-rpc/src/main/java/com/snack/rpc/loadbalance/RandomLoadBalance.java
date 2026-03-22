package com.snack.rpc.loadbalance;

import com.snack.rpc.spi.LoadBalanceSPI;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Random load balancer implementation.
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public class RandomLoadBalance implements LoadBalanceSPI {
    
    private final Random random = new Random();
    
    @Override
    public String getName() {
        return "random";
    }
    
    @Override
    public int getPriority() {
        return 90; // Slightly lower priority than round-robin
    }
    
    @Override
    public InetSocketAddress select(List<InetSocketAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        int index = random.nextInt(addresses.size());
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
        // Nothing to do for random
    }
    
    @Override
    public void updateWeight(InetSocketAddress address, int weight) {
        // Random doesn't support weights
    }
    
    @Override
    public void initialize(Properties config) {
        // No configuration needed
    }
}