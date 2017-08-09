package com.snack.rpc;

import com.snack.rpc.registry.ZooRegistry;
import com.snack.rpc.util.Classes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

/**
 * Created by yangyang.zhao on 2017/8/3.
 */
public class RpcApplication extends Application {
    private final Logger logger = LoggerFactory.getLogger(RpcApplication.class);
    private RpcServer server;

    public RpcApplication(final Class<?> bootClazz, String[] args) {
        super(bootClazz, args);
        super.bootClazz = bootClazz;
        server = new RpcServer();
    }

    @Override
    void load(Class<?> bootClazz) {
        try {
            // 向注册中心注册服务信息
            ZooRegistry.getInstance().registerService(RpcServer.getConfig().getString("server.name"), RpcServer.getConfig().getInt("server.port"));

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        ZooRegistry.getInstance().unregisterService(bootClazz.getName(), RpcServer.getConfig().getInt("server.port"));
                    } catch (Exception e) {
                        logger.info("fail to unregister server node. service name:" + bootClazz.getName(), e);
                    }
                }
            });

            scanAndRegisterServices();

            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void scanAndRegisterServices() {
        String[] packages = new String[]{bootClazz.getPackage().getName() + ".face"};

        for (String pkg : packages) {
            List<String> classes = Classes.getClassListByPackage(pkg, false);
            classes.forEach(this::tryRegisterService);
        }
    }

    private void tryRegisterService(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isInterface()) {
                Object instance = getBean(clazz);
                if (instance == null) {
                    logger.warn("cannot find bean [{}]", className);
                } else {
                    this.server.registerService(clazz, instance);
                }
            }
        } catch (Exception e) {
            logger.error("load class [{}] failed: {}", className, e);
        }
    }
}
