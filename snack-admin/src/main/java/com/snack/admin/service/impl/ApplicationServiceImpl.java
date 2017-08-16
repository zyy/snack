package com.snack.admin.service.impl;

import com.snack.admin.service.ApplicationService;
import com.snack.rpc.registry.ZooRegistry;
import org.apache.curator.x.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public Map<String, List> getServiceInstances() {
        Map<String, List> instancesMap = new HashMap<>();
        try {
            List<String> serviceNames = ZooRegistry.getInstance().queryForNames();
            for (String serviceName : serviceNames) {
                List<ServiceInstance> serviceInstances = ZooRegistry.getInstance().queryForInstances(serviceName);
                if (null == serviceInstances) {
                    instancesMap.put(serviceName, Collections.emptyList());
                } else {
                    instancesMap.put(serviceName, serviceInstances);
                }
            }
            return instancesMap;
        } catch (Exception e) {
            logger.error("getServiceInstances error", e);
        }
        return Collections.emptyMap();
    }
}
