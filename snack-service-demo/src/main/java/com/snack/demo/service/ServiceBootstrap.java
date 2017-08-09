package com.snack.demo.service;

import com.snack.rpc.RpcApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
@EnableAutoConfiguration
@SpringBootApplication
public class ServiceBootstrap {
    public static void main(String[] args) {
        RpcApplication app = new RpcApplication(ServiceBootstrap.class, args);
        app.run();
    }
}
