package com.snack.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Single Page Application Controller - for SPA routing
 */
@Controller
public class ServiceController {
    
    // 所有路由都转发到 index.html，由 Vue Router 处理前端路由
    @GetMapping(value = {"/", "/index", "/dashboard", "/services", "/circuit", "/system"})
    public String spa() {
        return "forward:/index.html";
    }
}
