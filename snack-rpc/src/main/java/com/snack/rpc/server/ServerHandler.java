package com.snack.rpc.server;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.trace.TraceCollector;
import com.snack.rpc.trace.TraceContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Server-side handler for processing RPC requests and heartbeat events.
 * Features:
 * - Handles RequestMessage via thread pool
 * - Handles IdleStateEvent for heartbeat
 * - Distributed tracing with TraceCollector
 * - Fixed parameter type matching using interface method signatures
 * - Rich error codes and error messages
 * 
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ServerHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    
    // Error codes
    private static final int ERR_SERVICE_NOT_FOUND = 404;
    private static final int ERR_METHOD_NOT_FOUND = 405;
    private static final int ERR_INVOCATION_FAILED = 500;
    private static final int ERR_TIMEOUT = 408;
    
    // Heartbeat config
    private static final int READER_IDLE_TIME = 30;
    private static final int WRITER_IDLE_TIME = 10;
    private static final String SERVER_ID = "snack-server-" + System.currentTimeMillis();
    
    private final RpcServer rpcServer;
    private final TraceCollector tracer;

    public ServerHandler(RpcServer rpcServer) {
        this(rpcServer, TraceCollector.INSTANCE);
    }
    
    public ServerHandler(RpcServer rpcServer, TraceCollector tracer) {
        this.rpcServer = rpcServer;
        this.tracer = tracer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof IdleStateEvent) {
            handleIdleStateEvent(ctx, (IdleStateEvent) msg);
        } else if (msg instanceof RequestMessage) {
            handleRequest(ctx, (RequestMessage) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void handleIdleStateEvent(ChannelHandlerContext ctx, IdleStateEvent evt) {
        IdleState state = evt.state();
        
        if (state == IdleState.READER_IDLE) {
            logger.warn("No read for {}s, closing connection: {}", new Object[]{READER_IDLE_TIME, ctx.channel()});
            ctx.close();
            
        } else if (state == IdleState.WRITER_IDLE) {
            HeartbeatMessage ping = HeartbeatMessage.ping(SERVER_ID);
            ctx.writeAndFlush(ping);
            logger.debug("Write idle, sent heartbeat ping to {}", new Object[]{ctx.channel()});
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, RequestMessage msg) {
        // Parse trace context from request
        TraceContext traceContext = TraceContext.fromHeader(msg.getTraceContext());
        
        long startTime = System.currentTimeMillis();
        logger.debug("ServerHandler received: messageId={}, service={}, method={}, trace={}", 
                new Object[]{msg.getMessageID(), msg.getServiceName(), msg.getMethodName(), traceContext});

        rpcServer.getThreadPoolExecutor().submit(
                new HandlerTask(ctx, msg, rpcServer, tracer, traceContext, startTime));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ServerHandler exception: ", cause);
        ctx.close();
    }

    private static class HandlerTask implements Runnable {
        private final ChannelHandlerContext ctx;
        private final RequestMessage requestMessage;
        private final RpcServer rpcServer;
        private final TraceCollector tracer;
        private final TraceContext traceContext;
        private final long startTime;

        public HandlerTask(ChannelHandlerContext ctx, RequestMessage requestMessage, RpcServer rpcServer,
                TraceCollector tracer, TraceContext traceContext, long startTime) {
            this.ctx = ctx;
            this.requestMessage = requestMessage;
            this.rpcServer = rpcServer;
            this.tracer = tracer;
            this.traceContext = traceContext;
            this.startTime = startTime;
        }

        @Override
        public void run() {
            ResponseMessage responseMessage = new ResponseMessage();
            responseMessage.setMessageID(requestMessage.getMessageID());
            responseMessage.setTraceId(traceContext.getTraceId());
            
            long durationMs = System.currentTimeMillis() - startTime;
            String errorInfo = null;
            boolean success = false;
            
            try {
                Object serviceInstance = rpcServer.services.get(requestMessage.getServiceName());
                if (serviceInstance == null) {
                    errorInfo = "Service not found: " + requestMessage.getServiceName();
                    sendError(responseMessage, ERR_SERVICE_NOT_FOUND, errorInfo);
                    ctx.writeAndFlush(responseMessage);
                    durationMs = System.currentTimeMillis() - startTime;
                    tracer.recordServerSpan(traceContext, requestMessage.getServiceName(), 
                            requestMessage.getMethodName(), false, durationMs, errorInfo);
                    return;
                }
                
                Method method = findMatchingMethod(
                        serviceInstance.getClass(),
                        requestMessage.getServiceName(),
                        requestMessage.getMethodName(),
                        requestMessage.getParameters()
                );
                
                if (method == null) {
                    errorInfo = buildMethodNotFoundMessage(requestMessage);
                    sendError(responseMessage, ERR_METHOD_NOT_FOUND, errorInfo);
                    ctx.writeAndFlush(responseMessage);
                    durationMs = System.currentTimeMillis() - startTime;
                    tracer.recordServerSpan(traceContext, requestMessage.getServiceName(),
                            requestMessage.getMethodName(), false, durationMs, errorInfo);
                    return;
                }
                
                Object result = method.invoke(serviceInstance, requestMessage.getParameters());
                
                responseMessage.setSuccess(true);
                responseMessage.setResult(result);
                success = true;
                
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                int errorCode = ERR_INVOCATION_FAILED;
                errorInfo = cause.getMessage();
                
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    errorCode = ERR_TIMEOUT;
                    errorInfo = "Method execution timeout";
                } else if (errorInfo == null || errorInfo.isEmpty()) {
                    errorInfo = cause.getClass().getSimpleName() + ": " + cause.toString();
                }
                
                sendError(responseMessage, errorCode, errorInfo);
            }
            
            durationMs = System.currentTimeMillis() - startTime;
            ctx.writeAndFlush(responseMessage);
            
            // Record trace span
            tracer.recordServerSpan(traceContext, requestMessage.getServiceName(),
                    requestMessage.getMethodName(), success, durationMs, errorInfo);
            
            logger.debug("Request processed: {} {} success={} duration={}ms trace={}",
                    new Object[]{requestMessage.getServiceName(), requestMessage.getMethodName(),
                            success, durationMs, traceContext.getTraceId()});
        }
        
        private Method findMatchingMethod(Class<?> implClass, String serviceName, 
                String methodName, Object[] params) {
            int paramCount = params != null ? params.length : 0;
            
            for (Class<?> iface : implClass.getInterfaces()) {
                if (iface.getName().equals(serviceName)) {
                    for (Method ifaceMethod : iface.getDeclaredMethods()) {
                        if (ifaceMethod.getName().equals(methodName) 
                                && ifaceMethod.getParameterCount() == paramCount) {
                            Method implMethod = findMethodWithExactParams(
                                    implClass, ifaceMethod.getName(), ifaceMethod.getParameterTypes());
                            if (implMethod != null) {
                                implMethod.setAccessible(true);
                                return implMethod;
                            }
                        }
                    }
                }
            }
            
            for (Method m : implClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
            
            for (Method m : implClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
            
            return null;
        }
        
        private Method findMethodWithExactParams(Class<?> implClass, String methodName, Class<?>[] paramTypes) {
            try {
                return implClass.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                for (Method m : implClass.getDeclaredMethods()) {
                    if (m.getName().equals(methodName) 
                            && Arrays.equals(m.getParameterTypes(), paramTypes)) {
                        return m;
                    }
                }
            }
            return null;
        }
        
        private String buildMethodNotFoundMessage(RequestMessage request) {
            StringBuilder sb = new StringBuilder();
            sb.append("Method not found: ").append(request.getMethodName());
            sb.append("(");
            if (request.getParameters() != null) {
                for (int i = 0; i < request.getParameters().length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(request.getParameters()[i] != null 
                            ? request.getParameters()[i].getClass().getSimpleName() 
                            : "null");
                }
            }
            sb.append(")");
            return sb.toString();
        }
        
        private void sendError(ResponseMessage msg, int errorCode, String errorInfo) {
            msg.setSuccess(false);
            msg.setErrorCode(errorCode);
            msg.setErrorInfo(errorInfo);
        }
    }
}
