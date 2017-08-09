package com.snack.web.demo;

import com.snack.rpc.WebApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
@EnableAutoConfiguration
@SpringBootApplication
public class WebBootstrap {
    public static void main(String[] args) {
        WebApplication app = new WebApplication(WebBootstrap.class, args);
        app.run();
    }
}
