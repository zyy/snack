package com.snack.web.demo;

import com.snack.demo.service.face.DemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by yangyang.zhao on 2017/8/4.
 */
@RestController
public class DemoController {
    @Autowired
    private DemoService demoService;

    @RequestMapping("/")
    String home() {
        return demoService.hello();
    }
}
