package com.snack.admin.controller;

import com.snack.admin.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * Created by yangyang.zhao on 2017/8/10.
 */
@Controller
public class ServiceController {
    @Autowired
    private ApplicationService applicationService;

    @RequestMapping("/")
    public String welcome(Map<String, Object> model) {
        model.put("instancesMap", this.applicationService.getServiceInstances());
        return "service_list";
    }
}
