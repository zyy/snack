package com.snack.rpc.client;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */

import com.snack.rpc.RpcClient;
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

public class RpcInvoker implements InvocationHandler {
    private Class clazz;
    private String server;
    private InvokerBalancer balancer;
    private ChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;
    private static final Logger logger = LoggerFactory.getLogger(RpcInvoker.class);

    public <T> RpcInvoker(String server, Class<T> clazz) {
        this.server = server;
        this.clazz = clazz;
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

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<ServiceInstance<InstanceDetails>> instances = ZooRegistry.getInstance().queryForInstances(server);
        ArrayList<InetSocketAddress> serverList = new ArrayList<>();
        for (ServiceInstance<InstanceDetails> instance : instances) {
            serverList.add(new InetSocketAddress(instance.getAddress(), instance.getPort()));
        }

        InetSocketAddress first = balancer.select(serverList);
        try {
            return getResponse(method, args, first);
        } catch (Exception e) {
            logger.info("invoke node error, node info:" + first);
        }

        // ʧ�ܵ��������ڵ�
        for (InetSocketAddress socketAddress : serverList) {
            if (socketAddress == first) {
                continue;
            }

            try {
                return getResponse(method, args, socketAddress);
            } catch (Exception e) {
                logger.info("invoke node error, node info:" + socketAddress);
            }
        }
        throw new RuntimeException("invoke all nodes are failure");
    }

    private Object getResponse(Method method, Object[] args, InetSocketAddress first) throws Exception {
        final SimpleChannelPool pool = poolMap.get(first);
        Future<Channel> future = pool.acquire().awaitUninterruptibly();

        RpcClientChannel channel = null;
        ResponseMessage responseMessage;
        RequestMessage requestMessage;
        try {
            channel = (RpcClientChannel) future.getNow();
            channel.reset();
            requestMessage = createRequestMessage(server, method.getName(), args, clazz);
            channel.writeAndFlush(requestMessage).awaitUninterruptibly();

            responseMessage = channel.get(10000);
        } catch (Exception e) {
            logger.error("invoker error", e);
            throw new RuntimeException(e);
        } finally {
            if (channel != null) {
                pool.release(channel);
            }
        }
        return responseMessage.getResult();
    }

    private RequestMessage createRequestMessage(String service, String method, Object[] args, Class clazz) {
        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setClientName(RpcClient.getConfig().getString("server.name"));
        requestMessage.setServerName(service);
        requestMessage.setServiceName(clazz.getName());
        requestMessage.setMethodName(method);
        requestMessage.setMessageID(UUID.randomUUID().toString());
        requestMessage.setParameters(args);
        return requestMessage;
    }

    private interface InvokerBalancer {
        InetSocketAddress select(List<InetSocketAddress> addresses);

        static InvokerBalancer get(String mode) {
            if (mode != null && mode.equals("RR")) {
                return new RoundRobinBalancer();
            }
            return new RandomBalancer();
        }

        class RoundRobinBalancer implements InvokerBalancer {
            private AtomicInteger counter = new AtomicInteger();

            @Override
            public InetSocketAddress select(List<InetSocketAddress> addresses) {
                int index = Math.abs(counter.getAndIncrement() % addresses.size());
                return addresses.get(index);
            }
        }

        class RandomBalancer implements InvokerBalancer {
            private Random random = new Random();

            @Override
            public InetSocketAddress select(List<InetSocketAddress> addresses) {
                int index = random.nextInt(addresses.size());
                return addresses.get(index);
            }
        }
    }
}
