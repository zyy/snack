package com.snack.rpc;

import com.snack.rpc.client.RpcInvoker;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RpcClient {
    private static Config conf = ConfigFactory.load();
    private static final ConcurrentHashMap<String, Object> proxies = new ConcurrentHashMap<>();

    public static Config getConfig() {
        return conf;
    }

    public RpcClient() {

    }

    public static <T> T get(String server, Class<T> clazz) {
        String key = server + "." + clazz.getName();
        Object obj = proxies.get(key);
        if (obj != null) {
            return (T) obj;
        }
        T proxy = (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new RpcInvoker(server, clazz));
        proxies.put(key, proxy);
        return proxy;
    }
}
