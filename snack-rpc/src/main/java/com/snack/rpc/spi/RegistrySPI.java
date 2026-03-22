package com.snack.rpc.spi;

import org.apache.curator.x.discovery.ServiceInstance;
import java.util.List;

/**
 * SPI interface for registry center extensions.
 * 
 * Implementations should:
 * 1. Provide a public no-arg constructor
 * 2. Be registered in META-INF/services/com.snack.rpc.spi.RegistrySPI
 * 3. Implement the service registry/discovery functions
 * 
 * Example implementations:
 * - ZooKeeperRegistry (existing ZooRegistry)
 * - NacosRegistry
 * - ConsulRegistry
 * - EtcdRegistry
 * - RedisRegistry
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public interface RegistrySPI {
    
    /**
     * Returns the unique name of this registry.
     * Used in configuration to select this registry.
     * 
     * @return registry name (e.g., "zookeeper", "nacos", "consul")
     */
    String getName();
    
    /**
     * Returns the priority of this registry (higher = more preferred).
     * 
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Initialize the registry with configuration.
     * 
     * @param config configuration properties
     * @throws Exception if initialization fails
     */
    void initialize(java.util.Properties config) throws Exception;
    
    /**
     * Start the registry.
     * 
     * @throws Exception if start fails
     */
    void start() throws Exception;
    
    /**
     * Stop the registry.
     */
    void stop();
    
    /**
     * Register a service instance.
     * 
     * @param serviceName name of the service
     * @param port service port
     * @param metadata additional metadata
     * @throws Exception if registration fails
     */
    void registerService(String serviceName, int port, java.util.Map<String, String> metadata) throws Exception;
    
    /**
     * Deregister a service instance.
     * 
     * @param serviceName name of the service
     * @throws Exception if deregistration fails
     */
    void deregisterService(String serviceName) throws Exception;
    
    /**
     * Query for service instances.
     * 
     * @param serviceName name of the service
     * @return list of service instances
     * @throws Exception if query fails
     */
    List<ServiceInstance<com.snack.rpc.registry.InstanceDetails>> queryForInstances(String serviceName) throws Exception;
    
    /**
     * Add a listener for service changes.
     * 
     * @param serviceName name of the service
     * @param listener listener to add
     */
    void addListener(String serviceName, ServiceChangeListener listener);
    
    /**
     * Remove a listener for service changes.
     * 
     * @param serviceName name of the service
     * @param listener listener to remove
     */
    void removeListener(String serviceName, ServiceChangeListener listener);
    
    /**
     * Get all registered services.
     * 
     * @return list of service names
     * @throws Exception if query fails
     */
    List<String> getAllServices() throws Exception;
    
    /**
     * Check if the registry is available.
     * 
     * @return true if available
     */
    boolean isAvailable();
    
    /**
     * Get registry status information.
     * 
     * @return status information
     */
    default java.util.Map<String, Object> getStatus() {
        return new java.util.HashMap<>();
    }
    
    /**
     * Listener interface for service change events.
     */
    interface ServiceChangeListener {
        /**
         * Called when service instances change.
         * 
         * @param serviceName name of the service
         * @param instances new list of instances (may be empty)
         */
        void onChange(String serviceName, List<ServiceInstance<com.snack.rpc.registry.InstanceDetails>> instances);
    }
}