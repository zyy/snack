package com.snack.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Single Page Application Controller - serves index.html for all routes.
 */
@Controller
public class ServiceController {
    
    @RequestMapping({"/", "/index", "/dashboard", "/services", "/circuit-breakers", "/system"})
    public String index() {
        return "index";
    }
}
