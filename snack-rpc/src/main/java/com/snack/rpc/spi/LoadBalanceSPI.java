package com.snack.rpc.spi;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * SPI interface for load balancing extensions.
 * 
 * Implementations should:
 * 1. Provide a public no-arg constructor
 * 2. Be registered in META-INF/services/com.snack.rpc.spi.LoadBalanceSPI
 * 3. Implement the load balancing algorithm
 * 
 * Example implementations:
 * - RoundRobin
 * - Random
 * - WeightedRoundRobin
 * - LeastActive
 * - ConsistentHash
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public interface LoadBalanceSPI {
    
    /**
     * Returns the unique name of this load balancer.
     * Used in configuration to select this balancer.
     * 
     * @return balancer name (e.g., "roundrobin", "random", "leastactive")
     */
    String getName();
    
    /**
     * Returns the priority of this balancer (higher = more preferred).
     * 
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Select a server address from the available list.
     * 
     * @param addresses list of available server addresses
     * @return selected server address, or null if none available
     */
    InetSocketAddress select(List<InetSocketAddress> addresses);
    
    /**
     * Called when a server fails.
     * Allows the load balancer to update its internal state.
     * 
     * @param failed the failed server address
     * @param addresses current list of available addresses
     */
    void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses);
    
    /**
     * Called when a server succeeds.
     * Allows the load balancer to update its internal state.
     * 
     * @param succeeded the successful server address
     * @param addresses current list of available addresses
     */
    default void onSuccess(InetSocketAddress succeeded, List<InetSocketAddress> addresses) {
        // default does nothing
    }
    
    /**
     * Update the server weight information.
     * 
     * @param address server address
     * @param weight new weight value
     */
    default void updateWeight(InetSocketAddress address, int weight) {
        // default does nothing
    }
    
    /**
     * Initialize the load balancer with configuration.
     * 
     * @param config configuration properties
     */
    default void initialize(java.util.Properties config) {
        // default does nothing
    }
}