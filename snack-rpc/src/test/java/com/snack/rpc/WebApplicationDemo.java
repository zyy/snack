package com.snack.rpc;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by yangyang.zhao on 2017/8/10.
 */
@EnableAutoConfiguration
@SpringBootApplication
public class WebApplicationDemo {
    public static void main(String[] args) {
        WebApplication app = new WebApplication(WebApplicationDemo.class, args);
        app.run();
    }
}
