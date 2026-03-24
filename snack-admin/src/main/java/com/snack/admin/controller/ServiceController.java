package com.snack.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Single Page Application Controller - serves index.html for all routes.
 */
@Controller
public class ServiceController {
    
    @GetMapping(value = {"/", "/index"})
    public String root() {
        return "forward:/index.html";
    }
    
    @GetMapping(value = {"/dashboard", "/services", "/circuit", "/system"})
    public String spa() {
        return "forward:/index.html";
    }
}
