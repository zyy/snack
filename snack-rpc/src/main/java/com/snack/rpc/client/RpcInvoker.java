package com.snack.rpc.client;

import com.snack.rpc.RpcServer;
import com.snack.rpc.trace.TraceCollector;
import com.snack.rpc.trace.TraceContext;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */

/**
 * RPC Client-side invoker with async, circuit breaker, rate limiter, and retry support.
 * 
 * Features:
 * - Sync and async (Future) RPC calls
 * - Configurable retry with exponential backoff
 * - Circuit breaker per service
 * - Rate limiter per service
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RpcInvoker implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(RpcInvoker.class);
    
    // Config keys
    private static final String CFG_TIMEOUT = "rpc.invoke.timeout";
    private static final String CFG_RETRY_ENABLE = "rpc.invoke.retry.enable";
    private static final String CFG_RETRY_STRATEGY = "rpc.invoke.retry.strategy";
    private static final String CFG_RETRY_MAX_ATTEMPTS = "rpc.invoke.retry.maxAttempts";
    private static final String CFG_RETRY_BASE_DELAY = "rpc.invoke.retry.baseDelayMs";
    private static final String CFG_RETRY_MAX_DELAY = "rpc.invoke.retry.maxDelayMs";
    private static final String CFG_RETRY_JITTER = "rpc.invoke.retry.jitter";
    private static final String CFG_CB_ENABLE = "rpc.circuitBreaker.enable";
    private static final String CFG_CB_THRESHOLD = "rpc.circuitBreaker.failureThreshold";
    private static final String CFG_CB_TIMEOUT = "rpc.circuitBreaker.recoveryTimeoutMs";
    private static final String CFG_CB_HALFOPEN = "rpc.circuitBreaker.halfOpenMaxCalls";
    private static final String CFG_RATE_LIMIT = "rpc.rateLimit.qps";
    private static final String CFG_RATE_BURST = "rpc.rateLimit.burst";
    
    // Defaults
    private static final int DEFAULT_TIMEOUT = 5000;
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    
    // Instance state
    private final Class<?> clazz;
    private final String server;
    private final int timeout;
    private final InvokerBalancer balancer;
    private final ChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;
    private final TraceCollector tracer;
    private final TraceContext traceContext;
    
    // Per-service circuit breakers (keyed by server address)
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // Rate limiter per server
    private final RateLimiter rateLimiter;
    
    // Retry policy
    private final RetryPolicy retryPolicy;
    private final boolean retryEnabled;
    
    public <T> RpcInvoker(String server, Class<T> clazz) {
        this.server = server;
        this.clazz = clazz;
        this.timeout = loadInt(CFG_TIMEOUT, DEFAULT_TIMEOUT_MS);
        this.retryEnabled = loadBool(CFG_RETRY_ENABLE, true);
        this.retryPolicy = buildRetryPolicy();
        this.rateLimiter = buildRateLimiter();
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
        
        this.tracer = TraceCollector.getInstance();
        this.traceContext = TraceContext.newRoot();
        
        logger.info("RpcInvoker created for service={}, timeout={}ms, retry={}, policy={}",
                new Object[]{server, timeout, retryEnabled, retryPolicy});
    }
    
    private int loadInt(String key, int def) {
        try { return RpcServer.getConfig().getInt(key); } catch (Exception e) { return def; }
    }
    
    private boolean loadBool(String key, boolean def) {
        try { return RpcServer.getConfig().getBoolean(key); } catch (Exception e) { return def; }
    }
    
    private RetryPolicy buildRetryPolicy() {
        String strategy = null;
        int maxAttempts = 3;
        long baseDelay = 100;
        long maxDelay = 2000;
        double jitter = 0.2;
        
        try { strategy = RpcServer.getConfig().getString(CFG_RETRY_STRATEGY); } catch (Exception ignored) {}
        try { maxAttempts = RpcServer.getConfig().getInt(CFG_RETRY_MAX_ATTEMPTS); } catch (Exception ignored) {}
        try { baseDelay = RpcServer.getConfig().getInt(CFG_RETRY_BASE_DELAY); } catch (Exception ignored) {}
        try { maxDelay = RpcServer.getConfig().getInt(CFG_RETRY_MAX_DELAY); } catch (Exception ignored) {}
        try { jitter = RpcServer.getConfig().getDouble(CFG_RETRY_JITTER); } catch (Exception ignored) {}
        
        return RetryPolicy.fromConfig(maxAttempts, baseDelay, maxDelay, strategy, jitter);
    }
    
    private RateLimiter buildRateLimiter() {
        double qps = 100;
        int burst = 50;
        try { qps = RpcServer.getConfig().getDouble(CFG_RATE_LIMIT); } catch (Exception ignored) {}
        try { burst = RpcServer.getConfig().getInt(CFG_RATE_BURST); } catch (Exception ignored) {}
        return new RateLimiter(qps, burst);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Async call if method returns RpcFuture
        if (method.getName().equals("$async") && method.getParameterCount() == 2) {
            String methodName = (String) args[0];
            Object[] methodArgs = (Object[]) args[1];
            return invokeAsync(proxy, methodName, methodArgs);
        }
        
        RpcFuture future = invokeAsync(proxy, method.getName(), args);
        return future.get();
    }
    
    /**
     * Invoke RPC call asynchronously.
     * Returns immediately with an RpcFuture.
     */
    public RpcFuture invokeAsync(Object proxy, String methodName, Object[] args) {
        RpcFuture future = new RpcFuture(timeout);
        
        // Rate limit check
        if (!rateLimiter.tryAcquire(1)) {
            Exception e = new RuntimeException("Rate limit exceeded for service: " + server);
            future.setException(e);
            return future;
        }
        
        // Dispatch async
        CompletableFuture.runAsync(() -> {
            try {
                Object result = doInvoke(methodName, args);
                future.setResponse(createSuccessResponse(result));
            } catch (Throwable e) {
                future.setException(new Exception(e));
            }
        });
        
        return future;
    }
    
    /**
     * Core invoke logic with retry, circuit breaker, and tracing.
     */
    private Object doInvoke(String methodName, Object[] args) throws Throwable {
        List<ServiceInstance<InstanceDetails>> instances = ZooRegistry.getInstance().queryForInstances(server);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("No available service instances for: " + server);
        }
        
        ArrayList<InetSocketAddress> serverList = new ArrayList<>();
        for (ServiceInstance<InstanceDetails> inst : instances) {
            serverList.add(new InetSocketAddress(inst.getAddress(), inst.getPort()));
        }
        
        Exception lastEx = null;
        
        for (int attempt = 1; attempt <= retryPolicy.getMaxAttempts(); attempt++) {
            // Create child span for this attempt
            final TraceContext spanContext = traceContext.childSpan();
            final long attemptStart = System.currentTimeMillis();
            
            // Pick server
            InetSocketAddress target = balancer.select(serverList);
            if (target == null) continue;
            
            // Circuit breaker check
            CircuitBreaker cb = circuitBreakers.computeIfAbsent(
                    target.toString(), 
                    k -> createCircuitBreaker()
            );
            if (!cb.allowRequest()) {
                logger.warn("Circuit breaker OPEN for {}, skipping to next node", new Object[]{target});
                balancer.onFailure(target, serverList);
                continue;
            }
            
            try {
                Object result = getResponse(methodName, args, target, spanContext);
                cb.recordSuccess();
                
                // Record successful client span
                long duration = System.currentTimeMillis() - attemptStart;
                tracer.recordClientSpan(spanContext, server, methodName, 
                        target.toString(), true, duration, null);
                
                return result;
            } catch (Exception e) {
                lastEx = e;
                cb.recordFailure();
                
                // Record failed client span
                long duration = System.currentTimeMillis() - attemptStart;
                tracer.recordClientSpan(spanContext, server, methodName, 
                        target.toString(), false, duration, e.getMessage());
                
                boolean shouldRetry = retryPolicy.isRetryable(e) 
                        && retryPolicy.shouldRetry(attempt);
                
                logger.warn("Invoke failed on {} attempt {}/{}: {} (retryable={})",
                        new Object[]{target, attempt, retryPolicy.getMaxAttempts(), e.getMessage(), shouldRetry});
                
                balancer.onFailure(target, serverList);
                
                if (shouldRetry && attempt < retryPolicy.getMaxAttempts()) {
                    long delay = retryPolicy.getDelayMs(attempt + 1);
                    if (delay > 0) {
                        logger.debug("Retrying in {}ms...", new Object[]{delay});
                        Thread.sleep(delay);
                    }
                }
            }
        }
        
        throw lastEx != null ? lastEx 
                : new RuntimeException("invoke all nodes failed for service: " + server);
    }
    
    private CircuitBreaker createCircuitBreaker() {
        int threshold = loadInt(CFG_CB_THRESHOLD, 5);
        long timeoutMs = loadInt(CFG_CB_TIMEOUT, 30000);
        int halfOpen = loadInt(CFG_CB_HALFOPEN, 3);
        return new CircuitBreaker(threshold, timeoutMs, halfOpen);
    }
    
    private CircuitBreaker getCircuitBreaker(InetSocketAddress addr) {
        return circuitBreakers.get(addr.toString());
    }
    
    private Object getResponse(String methodName, Object[] args, InetSocketAddress target, TraceContext ctx) throws Exception {
        final SimpleChannelPool pool = poolMap.get(target);
        Future<Channel> future = pool.acquire().awaitUninterruptibly();
        
        RpcClientChannel channel = null;
        try {
            channel = (RpcClientChannel) future.getNow();
            if (channel == null) {
                throw new RuntimeException("Failed to acquire channel from pool to " + target);
            }
            
            channel.reset();
            
            RequestMessage req = createRequestMessage(server, methodName, args, clazz, ctx);
            channel.writeAndFlush(req).awaitUninterruptibly();
            
            ResponseMessage resp = channel.get(timeout);
            
            if (resp == null) {
                throw new RpcInvoker.RpcTimeoutException(
                        "RPC call timeout after " + timeout + "ms on " + target);
            }
            
            if (!resp.isSuccess()) {
                throw new RpcInvoker.RpcCallException(
                        resp.getErrorCode(),
                        resp.getErrorInfo() != null ? resp.getErrorInfo() : "RPC call failed");
            }
            
            return resp.getResult();
            
        } finally {
            if (channel != null) {
                pool.release(channel);
            }
        }
    }

    private RequestMessage createRequestMessage(String service, String method, Object[] args, Class<?> clazz, TraceContext ctx) {
        RequestMessage req = new RequestMessage();
        req.setClientName(loadClientName());
        req.setServerName(service);
        req.setServiceName(clazz.getName());
        req.setMethodName(method);
        req.setMessageID(UUID.randomUUID().toString());
        req.setParameters(args);
        req.setTraceContext(ctx != null ? ctx.toHeader() : null);
        return req;
    }
    
    private ResponseMessage createSuccessResponse(Object result) {
        ResponseMessage resp = new ResponseMessage();
        resp.setSuccess(true);
        resp.setResult(result);
        return resp;
    }
    
    private String loadClientName() {
        try { return RpcServer.getConfig().getString("server.name"); } 
        catch (Exception e) { return "unknown"; }
    }

    // =====================
    // Balancer (kept inline for simplicity)
    // =====================
    
    private interface InvokerBalancer {
        InetSocketAddress select(List<InetSocketAddress> addresses);
        void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses);
        
        static InvokerBalancer get(String mode) {
            if (mode != null && mode.equals("RR")) return new RoundRobinBalancer();
            return new RandomBalancer();
        }
        
        class RoundRobinBalancer implements InvokerBalancer {
            private final AtomicInteger counter = new AtomicInteger();
            @Override
            public InetSocketAddress select(List<InetSocketAddress> addresses) {
                if (addresses == null || addresses.isEmpty()) return null;
                return addresses.get(Math.abs(counter.getAndIncrement() % addresses.size()));
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
                if (addresses == null || addresses.isEmpty()) return null;
                return addresses.get(random.nextInt(addresses.size()));
            }
            @Override
            public void onFailure(InetSocketAddress failed, List<InetSocketAddress> addresses) {
                addresses.remove(failed);
            }
        }
    }
    
    // =====================
    // Exceptions
    // =====================
    
    public static class RpcTimeoutException extends RuntimeException {
        public RpcTimeoutException(String msg) { super(msg); }
    }
    
    public static class RpcCallException extends RuntimeException {
        private final int errorCode;
        public RpcCallException(int code, String msg) { super(msg); this.errorCode = code; }
        public int getErrorCode() { return errorCode; }
    }
    
    // Simple CompletableFuture for async dispatch
    private static class CompletableFuture {
        static Runnable runAsync(Runnable r) {
            new Thread(r).start();
            return r;
        }
    }
}
