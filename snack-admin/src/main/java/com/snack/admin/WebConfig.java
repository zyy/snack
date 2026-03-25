package com.snack.admin;

import com.snack.admin.service.ApplicationService;
import com.snack.admin.service.impl.ApplicationServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * Created by yangyang.zhao on 2017/8/16.
 */
@Configuration
public class WebConfig extends WebMvcConfigurerAdapter {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    
    @Bean
    public ApplicationService applicationService() {
        System.out.println("WebConfig: Creating ApplicationServiceImpl bean explicitly");
        return new ApplicationServiceImpl();
    }
}
