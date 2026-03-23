package com.snack.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Dashboard pages.
 * Created by yangyang.zhao on 2026/3/23.
 */
@Controller
public class DashboardController {
    
    /**
     * Main dashboard page.
     */
    @RequestMapping("/dashboard")
    public String dashboard(Map<String, Object> model) {
        return "dashboard";
    }
}
