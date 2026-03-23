package com.snack.admin.controller;

import com.snack.admin.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Controller for service management pages.
 * Created by yangyang.zhao on 2017/8/10.
 * Updated: 2026/3/23 - Added enhanced service detail with metrics and circuit breaker support.
 */
@Controller
public class ServiceController {
    @Autowired
    private ApplicationService applicationService;

    @RequestMapping("/")
    public String serviceList(Map<String, Object> model) {
        model.put("instancesMap", this.applicationService.getServiceList());
        return "service_list";
    }

    @RequestMapping("/service/detail")
    public String serviceDetail(Map<String, Object> model, @RequestParam String serviceName) {
        model.put("serviceName", serviceName);
        model.put("serviceInstances", this.applicationService.getServiceInstances(serviceName));
        return "service_detail";
    }
}
