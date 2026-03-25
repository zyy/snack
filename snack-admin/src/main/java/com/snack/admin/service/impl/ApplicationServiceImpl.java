package com.snack.admin.service.impl;

import com.snack.admin.service.ApplicationService;
import com.snack.rpc.registry.InstanceDetails;
import com.snack.rpc.registry.ZooRegistry;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by yangyang.zhao on 2017/8/11.
 */
@Service
public class ApplicationServiceImpl implements ApplicationService {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationServiceImpl.class);
    
    public ApplicationServiceImpl() {
        logger.info("ApplicationServiceImpl constructor called");
    }
    
    @PostConstruct
    public void init() {
        logger.info("ApplicationServiceImpl @PostConstruct called, bean is initialized");
    }

    public Map<String, List> getServiceList() {
        Map<String, List> instancesMap = new HashMap<>();
        
        // 尝试使用ZooRegistry
        try {
            logger.info("Getting ZooRegistry instance...");
            ZooRegistry registry = ZooRegistry.getInstance();
            logger.info("ZooRegistry instance obtained, querying for service names...");
            List<String> serviceNames = registry.queryForNames();
            logger.info("ZooRegistry returned {} services: {}", serviceNames.size(), serviceNames);
            
            for (String serviceName : serviceNames) {
                List<ServiceInstance<InstanceDetails>> serviceInstances = registry.queryForInstances(serviceName);
                logger.info("Service {} has {} instances", serviceName, serviceInstances == null ? 0 : serviceInstances.size());
                if (null == serviceInstances) {
                    instancesMap.put(serviceName, Collections.emptyList());
                } else {
                    instancesMap.put(serviceName, (List) serviceInstances);
                }
            }
            
            if (!serviceNames.isEmpty()) {
                return instancesMap;
            }
        } catch (Exception e) {
            logger.error("getServiceList error using ZooRegistry", e);
        }
        
        // 备用方案：直接使用Curator客户端
        logger.info("Falling back to direct Curator client...");
        CuratorFramework client = null;
        ServiceDiscovery<InstanceDetails> serviceDiscovery = null;
        try {
            String connectString = "localhost:2181";
            String basePath = "/snack/serviceDiscovery";
            
            logger.info("Creating Curator client to {} with basePath {}", connectString, basePath);
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
            client = CuratorFrameworkFactory.newClient(connectString, retryPolicy);
            client.start();
            
            JsonInstanceSerializer<InstanceDetails> serializer = new JsonInstanceSerializer<>(InstanceDetails.class);
            serviceDiscovery = ServiceDiscoveryBuilder.builder(InstanceDetails.class)
                    .client(client)
                    .basePath(basePath)
                    .serializer(serializer)
                    .build();
            serviceDiscovery.start();
            
            List<String> serviceNames = (List<String>) serviceDiscovery.queryForNames();
            logger.info("Direct Curator client found {} services: {}", serviceNames.size(), serviceNames);
            
            for (String serviceName : serviceNames) {
                List<ServiceInstance<InstanceDetails>> serviceInstances = (List<ServiceInstance<InstanceDetails>>) serviceDiscovery.queryForInstances(serviceName);
                logger.info("Service {} has {} instances", serviceName, serviceInstances == null ? 0 : serviceInstances.size());
                if (serviceInstances == null) {
                    instancesMap.put(serviceName, Collections.emptyList());
                } else {
                    instancesMap.put(serviceName, (List) serviceInstances);
                }
            }
            
            if (!serviceNames.isEmpty()) {
                logger.info("Successfully retrieved services via direct Curator client");
                return instancesMap;
            }
            
        } catch (Exception e) {
            logger.error("getServiceList error using direct Curator client", e);
        } finally {
            if (serviceDiscovery != null) {
                try { serviceDiscovery.close(); } catch (Exception e) { logger.error("Error closing serviceDiscovery", e); }
            }
            if (client != null) {
                try { client.close(); } catch (Exception e) { logger.error("Error closing Curator client", e); }
            }
        }
        
        logger.warn("All attempts failed - returning empty service list");
        return Collections.emptyMap();
    }

    @Override
    public List getServiceInstances(String serviceName) {
        return (List) ZooRegistry.getInstance().queryForInstances(serviceName);
    }
}
