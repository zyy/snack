package com.snack.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.awt.*;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
public class Application {
    protected SpringApplication application;
    protected String[] args;
    protected ApplicationContext ctx;
    protected Class<?> bootClazz;

    public Application(Class<?> bootClazz, String[] args) {
        this.application = new SpringApplication(bootClazz);
        this.args = args;
    }

    public ApplicationContext run() {
        ctx = application.run(this.args);
        load(bootClazz);
        return ctx;
    }

    void load(Class<?> bootClazz) {

    }

    public <T> T getBean(Class<T> clazz) {
        return getBean(clazz, true);
    }

    public <T> T getBean(Class<T> clazz, boolean autowire) {
        if (ctx == null) {
            throw new RuntimeException("you must wait for application running");
        }

        T bean = ctx.getBean(clazz);
        if (bean == null && !clazz.isInterface()) {
            try {
                bean = clazz.newInstance();
                if (autowire) {
                    ctx.getAutowireCapableBeanFactory().autowireBean(bean);
                }
            } catch (Exception e) {
                String error = String.format("create bean instance of [%s] failed", clazz.getName());
                throw new RuntimeException(error, e);
            }
        }
        return bean;
    }
}
