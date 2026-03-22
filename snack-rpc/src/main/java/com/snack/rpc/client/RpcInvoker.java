package com.snack.rpc.client;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import com.snack.rpc.RpcClient;
import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.registry.InstanceDetails;
import com.snack.rpc.registry.ZooRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.concurrent.Future;
import org.apache.curator.x.discovery.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC Client-side invoker using JDK dynamic proxy.
 * 
 * Bug fix: 
 * - Handles timeout (null response) and retries on other nodes
 * - Configurable retry count via config "rpc.invoke.retry"
 * - Configurable timeout via config "rpc.invoke.timeout"
 */
public class RpcInvoker implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(RpcInvoker.class);
    
    private final Class<?> clazz;
    private final String server;
    private final InvokerBalancer balancer;
    private final ChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;
    private final int timeout;
    private final int maxRetry;
    
    private static final String CONFIG_KEY_TIMEOUT = "rpc.invoke.timeout";
    private static final String CONFIG_KEY_RETRY = "rpc.invoke.retry";
    
    public <T> RpcInvoker(String server, Class<T> clazz) {
        this.server = server;
        this.clazz = clazz;
        this.timeout = loadTimeout();
        this.maxRetry = loadMaxRetry();
        this.balancer = InvokerBalancer.get("RR");
        
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(RpcClientChannel.class);
        
        poolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
            @Override
            protected SimpleChannelPool newPool(InetSocketAddress key) {
                return new SimpleChannelPool(bootstrap.remoteAddress(key), new ClientChannelPoolHandler());
            }
        };
    }
    
    private int loadTimeout() {
        try {
            return RpcClient.getConfig().getInt(CONFIG_KEY_TIMEOUT);
        } catch (Exception e) {
            return 5000;
        }
    }
    
    private int loadMaxRetry() {
        try {
            return RpcClient.getConfig().getInt(CONFIG_KEY_RETRY);
        } catch (Exception e) {
            return 2;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<ServiceInstance<InstanceDetails>> instances = ZooRegistry.getInstance().queryForInstances(server);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("No available service instances for: " + server);
        }
        
        ArrayList<InetSocketAddress> serverList = new ArrayList<>();
        for (ServiceInstance<InstanceDetails> instance : instances) {
            serverList.add(new InetSocketAddress(instance.getAddress(), instance.getPort()));
        }
        
        Exception lastException = null;
        int totalRetry = Math.min(maxRetry, serverList.size());
        
        for (int attempt = 0; attempt < totalRetry; attempt++) {
            InetSocketAddress target = balancer.select(serverList);
            if (target == null) {
                continue;
            }
            
            try {
                return getResponse(method, args, target);
            } catch (Exception e) {
                lastException = e;
                logger.warn("invoke failed on {}, attempt {}/{}: {}", 
                        new Object[]{target, attempt + 1, totalRetry, e.getMessage()});
                balancer.onFailure(target, serverList);
            }
        }
        
        throw new RuntimeException("invoke all nodes are failure, last error: " 
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }
    
    private Object getResponse(Method method, Object[] args, InetSocketAddress target) throws Exception {
        final SimpleChannelPool pool = poolMap.get(target);
        Future<Channel> future = pool.acquire().awaitUninterruptibly();
        
        RpcClientChannel channel = null;
        try {
            channel = (RpcClientChannel) future.getNow();
            if (channel == null) {
                throw new RuntimeException("Failed to acquire channel from pool to " + target);
            }
            
            channel.reset();
            
            RequestMessage requestMessage = createRequestMessage(server, method.getName(), args, clazz);
            channel.writeAndFlush(requestMessage).awaitUninterruptibly();
            
            ResponseMessage responseMessage = channel.get(timeout);
            
            if (responseMessage == null) {
                throw new RpcTimeoutException("RPC call timeout after " + timeout + "ms on " + target);
            }
            
            if (!responseMessage.isSuccess()) {
                throw new RpcCallException(responseMessage.getErrorCode(), 
                        responseMessage.getErrorInfo() != null ? responseMessage.getErrorInfo() : "RPC call failed");
            }
            
            return responseMessage.getResult();
            
        } finally {
            if (channel != null) {
                pool.release(channel);
            }
        }
    }

    private RequestMessage createRequestMessage(String service, String method, Object[] args, Class<?> clazz) {
        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setClientName(loadClientName());
        requestMessage.setServerName(service);
        requestMessage.setServiceName(clazz.getName());
        requestMessage.setMethodName(method);
        requestMessage.setMessageID(UUID.randomUUID().toString());
        requestMessage.setParameters(args);
        return requestMessage;
    }
    
    private String loadClientName() {
        try {
            return RpcServer.getConfig().getString("server.name");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private interface InvokerBalancer {
        InetSocketAddress select(List<InetSocketAddress> addresses);
        void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses);
        
        static InvokerBalancer get(String mode) {
            if (mode != null && mode.equals("RR")) {
                return new RoundRobinBalancer();
            }
            return new RandomBalancer();
        }
        
        class RoundRobinBalancer implements InvokerBalancer {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public InetSocketAddress select(List<InetSocketAddress> addresses) {
                if (addresses == null || addresses.isEmpty()) {
                    return null;
                }
                int index = Math.abs(counter.getAndIncrement() % addresses.size());
                return addresses.get(index);
            }
            
            @Override
            public void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses) {
                addresses.remove(failed);
            }
        }
        
        class RandomBalancer implements InvokerBalancer {
            private final Random random = new Random();

            @Override
            public InetSocketAddress select(List<InetSocketAddress> addresses) {
                if (addresses == null || addresses.isEmpty()) {
                    return null;
                }
                int index = random.nextInt(addresses.size());
                return addresses.get(index);
            }
            
            @Override
            public void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses) {
                addresses.remove(failed);
            }
        }
    }
    
    public static class RpcTimeoutException extends RuntimeException {
        public RpcTimeoutException(String message) {
            super(message);
        }
    }
    
    public static class RpcCallException extends RuntimeException {
        private final int errorCode;
        
        public RpcCallException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}
