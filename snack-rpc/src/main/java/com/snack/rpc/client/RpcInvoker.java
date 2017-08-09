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
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.concurrent.Future;
import org.apache.curator.x.discovery.ServiceInstance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class RpcInvoker implements InvocationHandler {
    private String server;
    private Class clazz;
    private ChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;

    public <T> RpcInvoker(String server, Class<T> clazz) {
        this.server = server;
        this.clazz = clazz;

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
        Collection<ServiceInstance<InstanceDetails>> instances = ZooRegistry.getInstance().queryForInstances(server);
        ArrayList<InetSocketAddress> serverList = new ArrayList<>();
        for (ServiceInstance<InstanceDetails> instance : instances) {
            serverList.add(new InetSocketAddress(instance.getAddress(), instance.getPort()));
        }

        final SimpleChannelPool pool = poolMap.get(serverList.get(0));
        Future<Channel> future = pool.acquire().awaitUninterruptibly();

        RpcClientChannel channel = (RpcClientChannel) future.getNow();
        channel.reset();

        RequestMessage requestMessage = createRequestMessage(server, method.getName(), args, clazz);
        ChannelFuture channelFuture = channel.writeAndFlush(requestMessage).awaitUninterruptibly();

        ResponseMessage responseMessage = channel.get(10000);

        if (channel != null) {
            pool.release(channel);
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
}
