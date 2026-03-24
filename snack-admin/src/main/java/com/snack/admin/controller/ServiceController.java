package com.snack.admin.controller;

import com.snack.admin.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Controller for service management pages.
 */
@Controller
public class ServiceController {
    
    @Autowired
    private ApplicationService applicationService;

    // Page routes
    @RequestMapping({"/", "/index"})
    public String index() {
        return "redirect:/dashboard";
    }
    
    @RequestMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
    
    @RequestMapping("/services")
    public String services() {
        return "services";
    }
    
    @RequestMapping("/circuit-breakers")
    public String circuitBreakers() {
        return "circuit_breakers";
    }
    
    @RequestMapping("/system")
    public String system() {
        return "system";
    }
    
    @RequestMapping("/service/detail")
    public String serviceDetail(Map<String, Object> model, @RequestParam String serviceName) {
        model.put("serviceName", serviceName);
        model.put("serviceInstances", this.applicationService.getServiceInstances(serviceName));
        return "service_detail";
    }
    
    @RequestMapping("/service/list")
    public String serviceList(Map<String, Object> model) {
        model.put("instancesMap", this.applicationService.getServiceList());
        return "service_list";
    }
}
