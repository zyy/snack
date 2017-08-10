package com.snack.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
public abstract class Application {
    protected SpringApplication springApplication;
    protected String[] args;
    protected ApplicationContext ctx;
    protected Class<?> bootClazz;

    public Application(Class<?> bootClazz, String[] args) {
        this.springApplication = new SpringApplication(bootClazz);
        this.args = args;
    }

    public ApplicationContext run() {
        ctx = springApplication.run(this.args);
        load(bootClazz);
        return ctx;
    }

    public abstract void load(Class<?> bootClazz);

    public <T> T getBean(Class<T> clazz) {
        return getBean(clazz, true);
    }

    public <T> T getBean(Class<T> clazz, boolean autowire) {
        if (ctx == null) {
            throw new RuntimeException("you must wait for springApplication running");
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
