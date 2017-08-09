package com.snack.demo.service.spring;

import com.snack.demo.service.face.DemoService;
import com.snack.rpc.RpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by yangyang.zhao on 2017/8/4.
 */
@Configuration
public class ServiceAutoConfig {
    private final String SERVER = "com.snack.demo.service";

    @Bean
    @ConditionalOnMissingBean
    public DemoService demoService() {
        return RpcClient.get(SERVER, DemoService.class);
    }
}
