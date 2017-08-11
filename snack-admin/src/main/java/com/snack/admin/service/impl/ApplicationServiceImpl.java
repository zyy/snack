package com.snack.admin.service.impl;

import com.snack.admin.service.ApplicationService;
import com.snack.rpc.registry.ZooRegistry;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by yangyang.zhao on 2017/8/11.
 */
@Service
public class ApplicationServiceImpl implements ApplicationService {

    public Collection<String> getServiceInstances() {
        try {
            Collection<String> instances = ZooRegistry.getInstance().queryForNames();
            return instances;
        } catch (Exception e) {

        }
        return Collections.emptyList();
    }
}
