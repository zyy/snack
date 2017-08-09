package com.snack.rpc;

import com.snack.rpc.client.ClientChannelInitializer;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.client.ClientHandler;
import com.snack.rpc.registry.InstanceDetails;
import com.snack.rpc.registry.ZooRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.curator.x.discovery.ServiceInstance;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RpcClient {
    private static Config conf = ConfigFactory.load();
    private static ReentrantLock lock = new ReentrantLock();
    private static Condition connected  = lock.newCondition();
    private static final ConcurrentHashMap<String, Object> proxies = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<String, ClientHandler> connectedHandlers = new ConcurrentHashMap();

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

    static class RpcInvoker implements InvocationHandler {
        private String server;
        Class clazz;

        public <T> RpcInvoker(String server, Class<T> clazz) {
            this.server = server;
            this.clazz = clazz;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Collection<ServiceInstance<InstanceDetails>> instances = ZooRegistry.getInstance().queryForInstances(server);
            ArrayList<InetSocketAddress> serverList = new ArrayList<>();
            for (ServiceInstance<InstanceDetails> instance : instances) {
                serverList.add(new InetSocketAddress(instance.getAddress(), instance.getPort()));
            }

            String host = serverList.get(0).getAddress().getHostAddress();
            int port = serverList.get(0).getPort();

            ClientHandler clientHandler = connectedHandlers.get(host + ":" + port);
            if (clientHandler == null) {
                doConnect(host, port);
                waitingForHandler();
            }
            clientHandler = connectedHandlers.get(host + ":" + port);

            RequestMessage requestMessage = createRequestMessage(server, method.getName(), args, clazz);

            clientHandler.doRequest(requestMessage);

            Thread.sleep(10000);

            return clientHandler.getResult();
        }

        private boolean waitingForHandler() throws InterruptedException{
            lock.lock();
            try{
                return connected.await(30000, TimeUnit.MILLISECONDS);
            }finally{
                lock.unlock();
            }
        }

        private void signalAvailableHandler() {
            lock.lock();
            try{
                connected.signalAll();
            }finally{
                lock.unlock();
            }
        }

        private synchronized void doConnect(String host, int port) {

            try {
                EventLoopGroup group = new NioEventLoopGroup();
                Bootstrap bootstrap = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ClientChannelInitializer());

                ChannelFuture channelFuture = bootstrap.connect(host, port);

                channelFuture.addListener(new ChannelFutureListener(){
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if(!channelFuture.isSuccess()){
                        }else{
                            ClientHandler handler = channelFuture.channel().pipeline().get(ClientHandler.class);
                            connectedHandlers.put(host + ":" + port, handler);
                            signalAvailableHandler();
                        }
                    }
                });
            } catch (Exception e) {
               e.printStackTrace();
            }
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
}
