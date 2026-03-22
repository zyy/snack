package com.snack.rpc.registry;

import com.snack.rpc.spi.RegistrySPI;
import org.apache.curator.x.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ZooKeeper registry SPI implementation.
 * 
 * Adapter for existing ZooRegistry to implement RegistrySPI interface.
 * 
 * Created by yangyang.zhao on 2026/3/22.
 */
public class ZooKeeperRegistrySPI implements RegistrySPI {
    private static final Logger logger = LoggerFactory.getLogger(ZooKeeperRegistrySPI.class);
    
    private ZooRegistry zooRegistry;
    private Properties config;
    private final Map<String, List<ServiceChangeListener>> listeners = new ConcurrentHashMap<>();
    private volatile boolean started = false;
    
    @Override
    public String getName() {
        return "zookeeper";
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority as default registry
    }
    
    @Override
    public void initialize(Properties config) throws Exception {
        this.config = config;
        
        // Convert Properties to Typesafe Config
        com.typesafe.config.Config typesafeConfig = createTypesafeConfig(config);
        
        // Initialize ZooRegistry with the config
        // Note: This is a bit hacky since ZooRegistry uses static singleton pattern
        // In a real refactor, ZooRegistry should be refactored to be instance-based
        System.setProperty("zookeeper.connectString", 
                config.getProperty("connectString", "localhost:2181"));
        System.setProperty("zookeeper.basePath", 
                config.getProperty("basePath", "/snack/serviceDiscovery"));
        
        logger.info("ZooKeeperRegistrySPI initialized with connectString={}, basePath={}",
                config.getProperty("connectString"), config.getProperty("basePath"));
    }
    
    @Override
    public void start() throws Exception {
        if (started) {
            return;
        }
        
        // Get singleton instance
        zooRegistry = ZooRegistry.getInstance();
        started = true;
        
        logger.info("ZooKeeperRegistrySPI started");
    }
    
    @Override
    public void stop() {
        // Note: ZooRegistry doesn't have stop method in current implementation
        // In production, we should add stop method to ZooRegistry
        started = false;
        logger.info("ZooKeeperRegistrySPI stopped");
    }
    
    @Override
    public void registerService(String serviceName, int port, Map<String, String> metadata) throws Exception {
        if (!started) {
            throw new IllegalStateException("Registry not started");
        }
        
        zooRegistry.registerService(serviceName, port);
        
        // Store metadata (currently not supported by ZooRegistry)
        if (metadata != null && !metadata.isEmpty()) {
            logger.debug("Metadata provided but not stored in ZooRegistry: {}", metadata);
        }
    }
    
    @Override
    public void deregisterService(String serviceName) throws Exception {
        if (!started) {
            throw new IllegalStateException("Registry not started");
        }
        
        // ZooRegistry.unregisterService requires port, which we don't have
        // This is a limitation of the current ZooRegistry design
        // For now, we'll just log a warning
        logger.warn("deregisterService called but ZooRegistry requires port. " +
                   "Service {} may not be fully deregistered.", serviceName);
    }
    
    @Override
    public List<ServiceInstance<InstanceDetails>> queryForInstances(String serviceName) throws Exception {
        if (!started) {
            throw new IllegalStateException("Registry not started");
        }
        
        return zooRegistry.queryForInstances(serviceName);
    }
    
    @Override
    public void addListener(String serviceName, ServiceChangeListener listener) {
        listeners.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        
        logger.debug("Added listener for service {}: {}", serviceName, listener);
        
        // Note: ZooRegistry doesn't support listeners in current implementation
        // In production, we should add listener support to ZooRegistry
    }
    
    @Override
    public void removeListener(String serviceName, ServiceChangeListener listener) {
        List<ServiceChangeListener> serviceListeners = listeners.get(serviceName);
        if (serviceListeners != null) {
            serviceListeners.remove(listener);
            logger.debug("Removed listener for service {}: {}", serviceName, listener);
        }
    }
    
    @Override
    public List<String> getAllServices() throws Exception {
        if (!started) {
            throw new IllegalStateException("Registry not started");
        }
        
        // ZooRegistry doesn't have getAllServices method in current implementation
        // Return empty list for now
        logger.warn("getAllServices not implemented in ZooRegistry");
        return Collections.emptyList();
    }
    
    @Override
    public boolean isAvailable() {
        return started && zooRegistry != null;
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("name", getName());
        status.put("started", started);
        status.put("listenerCount", listeners.values().stream()
                .mapToInt(List::size)
                .sum());
        return status;
    }
    
    /**
     * Notify listeners of service change.
     * This should be called by ZooRegistry when services change.
     */
    public void notifyServiceChange(String serviceName, List<ServiceInstance<InstanceDetails>> instances) {
        List<ServiceChangeListener> serviceListeners = listeners.get(serviceName);
        if (serviceListeners != null) {
            for (ServiceChangeListener listener : serviceListeners) {
                try {
                    listener.onChange(serviceName, instances);
                } catch (Exception e) {
                    logger.error("Error notifying listener for service {}", serviceName, e);
                }
            }
        }
    }
    
    private com.typesafe.config.Config createTypesafeConfig(Properties props) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            sb.append(key).append(" = \"").append(value).append("\"\n");
        }
        return com.typesafe.config.ConfigFactory.parseString(sb.toString());
    }
}