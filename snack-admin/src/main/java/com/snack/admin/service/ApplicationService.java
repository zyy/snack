package com.snack.admin.service;

import org.apache.curator.x.discovery.ServiceInstance;

import java.util.List;
import java.util.Map;

/**
 * Created by yangyang.zhao on 2017/8/11.
 */
public interface ApplicationService {
    Map<String, List> getServiceList();

    List<ServiceInstance> getServiceInstances(String serviceName);
}
