package com.snack.admin;

import com.snack.rpc.WebApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
@EnableAutoConfiguration
@SpringBootApplication
public class WebFrontBootstrap {
    public static void main(String[] args) {
        WebApplication app = new WebApplication(WebFrontBootstrap.class, args);
        app.run();
    }
}
