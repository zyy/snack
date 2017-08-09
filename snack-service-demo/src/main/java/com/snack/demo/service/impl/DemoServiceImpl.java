package com.snack.demo.service.impl;

import com.snack.demo.service.face.DemoService;
import org.springframework.stereotype.Component;

/**
 * Created by yangyang.zhao on 2017/8/4.
 */
@Component
public class DemoServiceImpl implements DemoService {
    public String hello() {
        return "hello world";
    }
}
