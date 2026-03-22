package com.snack.rpc;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import com.snack.rpc.server.ServerChannelInitializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * RPC Server based on Netty4.
 * 
 * Bug fix:
 * - Replaced unbounded thread pool with bounded cached thread pool
 * - Added configurable core pool size, max pool size, and queue capacity
 */
public class RpcServer {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);
    
    private static Config conf = ConfigFactory.load();
    public static final Map<String, Object> services = new ConcurrentHashMap<>();
    
    // Thread pool configuration keys
    private static final String CORE_POOL_SIZE_KEY = "rpc.server.threadPool.corePoolSize";
    private static final String MAX_POOL_SIZE_KEY = "rpc.server.threadPool.maxPoolSize";
    private static final String QUEUE_CAPACITY_KEY = "rpc.server.threadPool.queueCapacity";
    private static final String KEEP_ALIVE_SECONDS_KEY = "rpc.server.threadPool.keepAliveSeconds";
    
    private final ThreadPoolExecutor threadPoolExecutor;
    
    public RpcServer() {
        int corePoolSize = loadInt(CORE_POOL_SIZE_KEY, 16);
        int maxPoolSize = loadInt(MAX_POOL_SIZE_KEY, 64);
        int queueCapacity = loadInt(QUEUE_CAPACITY_KEY, 1024);
        int keepAliveSeconds = loadInt(KEEP_ALIVE_SECONDS_KEY, 60);
        
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        
        threadPoolExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                workQueue,
                new DefaultThreadFactory("rpc-server-worker", true),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        
        logger.info("RpcServer thread pool initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}, keepAliveSeconds={}",
                new Object[]{corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds});
    }
    
    private int loadInt(String key, int defaultValue) {
        try {
            return conf.getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor;
    }
    
    public static Config getConfig() {
        return conf;
    }
    
    public void start() throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1, new DefaultThreadFactory("rpc-server-boss", true));
        EventLoopGroup worker = new NioEventLoopGroup(
                loadInt("rpc.server.workerThreads", 0),
                new DefaultThreadFactory("rpc-server-worker-nio", true)
        );
        
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerChannelInitializer(this))
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            int port = conf.getInt("server.port");
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            
            logger.info("RpcServer started on port {}", new Object[]{port});

            channelFuture.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }
    
    public void shutdown() {
        logger.info("Shutting down RpcServer thread pool...");
        threadPoolExecutor.shutdown();
        try {
            if (!threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Thread pool did not terminate in time, forcing shutdown...", new Object[]{});
                threadPoolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted, forcing shutdown...", new Object[]{});
            threadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public void registerService(Class<?> clazz, Object instance) {
        services.put(clazz.getName(), instance);
        logger.info("Service registered: {}", new Object[]{clazz.getName()});
    }
    
    public void unregisterService(Class<?> clazz) {
        services.remove(clazz.getName());
        logger.info("Service unregistered: {}", new Object[]{clazz.getName()});
    }
    
    public Map<String, Object> getThreadPoolStats() {
        return Map.of(
                "activeCount", threadPoolExecutor.getActiveCount(),
                "poolSize", threadPoolExecutor.getPoolSize(),
                "queueSize", threadPoolExecutor.getQueue().size(),
                "completedTaskCount", threadPoolExecutor.getCompletedTaskCount(),
                "largestPoolSize", threadPoolExecutor.getLargestPoolSize()
        );
    }
}
