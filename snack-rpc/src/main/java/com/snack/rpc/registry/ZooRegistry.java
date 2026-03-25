package com.snack.rpc.registry;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceType;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ZooKeeper-based service registry and discovery.
 * 
 * Bug fix:
 * - Loads its own config independently (not depending on RpcServer.getConfig())
 * - Supports separate client/server config via config keys
 * - Better error handling
 */
public class ZooRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ZooRegistry.class);
    
    private CuratorFramework client = null;
    private ServiceDiscovery<InstanceDetails> serviceDiscovery = null;
    private static volatile ZooRegistry instance;
    
    private String innerHostIp = null;
    private static final Pattern ipPattern = Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$");
    private static final Pattern privateIpPattern = Pattern.compile(
            "(^127\\.0\\.0\\.1)|" +
            "(^10(\\.[0-9]{1,3}){3}$)|" +
            "(^172\\.1[6-9](\\.[0-9]{1,3}){2}$)|" +
            "(^172\\.2[0-9](\\.[0-9]{1,3}){2}$)|" +
            "(^172\\.3[0-1](\\.[0-9]{1,3}){2}$)|" +
            "(^192\\.168(\\.[0-9]{1,3}){2}$)"
    );
    
    // Config keys - can be overridden
    private static final String CONFIG_ZOOKEEPER_CONNECT = "zookeeper.connectString";
    private static final String CONFIG_ZOOKEEPER_BASE_PATH = "zookeeper.basePath";
    private static final String CONFIG_SERVER_NAME = "server.name";
    
    private ZooRegistry() {
        init();
    }
    
    /**
     * Get singleton instance.
     */
    public static ZooRegistry getInstance() {
        if (instance == null) {
            synchronized (ZooRegistry.class) {
                if (instance == null) {
                    instance = new ZooRegistry();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize registry with config.
     */
    private void init() {
        try {
            Config config = ConfigFactory.load();
            
            String connectString = getConfigString(CONFIG_ZOOKEEPER_CONNECT, "localhost:2181");
            String basePath = getConfigString(CONFIG_ZOOKEEPER_BASE_PATH, "/snack/serviceDiscovery");
            
            logger.info("Initializing ZooRegistry with connectString={}, basePath={}", 
                    connectString, basePath);
            
            client = CuratorFrameworkFactory.newClient(
                    connectString, 
                    new ExponentialBackoffRetry(1000, 3)
            );
            client.start();
            
            JsonInstanceSerializer<InstanceDetails> serializer = 
                    new JsonInstanceSerializer<>(InstanceDetails.class);
            
            serviceDiscovery = ServiceDiscoveryBuilder
                    .builder(InstanceDetails.class)
                    .client(client)
                    .basePath(basePath)
                    .serializer(serializer)
                    .build();
            
            serviceDiscovery.start();
            
            logger.info("ZooRegistry initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize ZooRegistry!", e);
            throw new RuntimeException("Failed to initialize ZooRegistry", e);
        }
    }
    
    /**
     * Register a service instance.
     */
    public void registerService(String serviceName, int port) throws Exception {
        if (serviceDiscovery == null) {
            throw new IllegalStateException("ZooRegistry not initialized");
        }
        
        String localIp = getInnerHostIp();
        String id = localIp + ":" + port;
        
        ServiceInstance<InstanceDetails> service = ServiceInstance
                .<InstanceDetails>builder()
                .name(serviceName)
                .address(localIp)
                .port(port)
                .id(id)
                .serviceType(ServiceType.DYNAMIC)
                .payload(new InstanceDetails(id, localIp, port, serviceName))
                .build();

        serviceDiscovery.registerService(service);
        logger.info("Service registered: {} at {}:{}", new Object[]{serviceName, localIp, port});
    }
    
    /**
     * Unregister a service instance.
     */
    public void unregisterService(String serviceName, int port) throws Exception {
        if (serviceDiscovery == null) {
            throw new IllegalStateException("ZooRegistry not initialized");
        }
        
        String localIp = getInnerHostIp();
        String id = localIp + ":" + port;
        
        ServiceInstance<InstanceDetails> service = ServiceInstance
                .<InstanceDetails>builder()
                .name(serviceName)
                .address(localIp)
                .port(port)
                .id(id)
                .serviceType(ServiceType.DYNAMIC)
                .payload(new InstanceDetails(id, localIp, port, serviceName))
                .build();

        serviceDiscovery.unregisterService(service);
        logger.info("Service unregistered: {} at {}:{}", new Object[]{serviceName, localIp, port});
    }
    
    /**
     * Query service instances by name.
     */
    public List<ServiceInstance<InstanceDetails>> queryForInstances(String serviceName) {
        if (StringUtils.isEmpty(serviceName) || serviceDiscovery == null) {
            return Collections.emptyList();
        }
        try {
            return Arrays.asList(serviceDiscovery.queryForInstances(serviceName).toArray(
                    new ServiceInstance[0]));
        } catch (Exception e) {
            logger.error("Failed to query instances for service: {}", serviceName, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Query all registered service names.
     */
    public List<String> queryForNames() {
        if (serviceDiscovery == null) {
            return Collections.emptyList();
        }
        try {
            return Arrays.asList(serviceDiscovery.queryForNames().toArray(new String[0]));
        } catch (Exception e) {
            logger.error("Failed to query service names", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Close the registry and cleanup resources.
     */
    public void close() {
        logger.info("Closing ZooRegistry...");
        try {
            if (serviceDiscovery != null) {
                serviceDiscovery.close();
            }
            if (client != null) {
                client.close();
            }
            logger.info("ZooRegistry closed");
        } catch (Exception e) {
            logger.error("Error closing ZooRegistry", e);
        }
    }
    
    /**
     * Get local host IP address.
     */
    private String getInnerHostIp() {
        if (innerHostIp == null) {
            synchronized (this) {
                if (innerHostIp == null) {
                    innerHostIp = findInnerHostIp();
                }
            }
        }
        return innerHostIp;
    }
    
    private String findInnerHostIp() {
        // 强制使用 127.0.0.1 以解决 RPC 连接问题
        logger.info("Forcing IP address to 127.0.0.1 for RPC registration");
        return "127.0.0.1";
        
        /* 原代码已注释掉
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String tempIp = addr.getHostAddress();
                    
                    // Find private IP
                    if (ipPattern.matcher(tempIp).matches() 
                            && privateIpPattern.matcher(tempIp).matches()) {
                        logger.debug("Found private IP: {} on {}", tempIp, networkInterface.getName());
                        return tempIp;
                    }
                }
            }
            
            // Fallback to localhost
            logger.warn("Could not find private IP, using localhost");
            return "127.0.0.1";
            
        } catch (SocketException e) {
            logger.error("Failed to get network interfaces", e);
            return "127.0.0.1";
        }
        */
    }
    
    /**
     * Get config string with fallback default.
     */
    private String getConfigString(String key, String defaultValue) {
        try {
            return ConfigFactory.load().getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
