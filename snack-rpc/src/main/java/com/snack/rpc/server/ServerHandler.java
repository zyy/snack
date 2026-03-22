package com.snack.rpc.server;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Server-side handler for processing RPC requests and heartbeat events.
 * 
 * Features:
 * - Handles RequestMessage via thread pool
 * - Handles IdleStateEvent for heartbeat
 * - Fixed parameter type matching using interface method signatures
 * - Rich error codes and error messages
 */
public class ServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    
    // Error codes
    private static final int ERR_SERVICE_NOT_FOUND = 404;
    private static final int ERR_METHOD_NOT_FOUND = 405;
    private static final int ERR_INVOCATION_FAILED = 500;
    private static final int ERR_TIMEOUT = 408;
    
    // Heartbeat config (must match ServerChannelInitializer)
    private static final int READER_IDLE_TIME = 30;
    private static final int WRITER_IDLE_TIME = 10;
    private static final String SERVER_ID = "snack-server-" + System.currentTimeMillis();
    
    private final RpcServer rpcServer;

    public ServerHandler(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof IdleStateEvent) {
            handleIdleStateEvent(ctx, (IdleStateEvent) msg);
        } else if (msg instanceof RequestMessage) {
            handleRequest(ctx, (RequestMessage) msg);
        } else {
            logger.warn("Unexpected message type: {}", msg.getClass().getName());
        }
    }

    private void handleIdleStateEvent(ChannelHandlerContext ctx, IdleStateEvent evt) {
        IdleState state = evt.state();
        
        if (state == IdleState.READER_IDLE) {
            // No read for a long time - close connection
            logger.warn("No read for {}s, closing connection: {}", READER_IDLE_TIME, ctx.channel());
            ctx.close();
            
        } else if (state == IdleState.WRITER_IDLE) {
            // No write for a while - send heartbeat ping
            HeartbeatMessage ping = HeartbeatMessage.ping(SERVER_ID);
            ctx.writeAndFlush(ping);
            logger.debug("Write idle, sent heartbeat ping to {}", ctx.channel());
        }
        // ALL_IDLE not handled (disabled)
    }

    private void handleRequest(ChannelHandlerContext ctx, RequestMessage msg) {
        logger.debug("ServerHandler received: messageId={}, service={}, method={}", new Object[]{ 
                msg.getMessageID(), msg.getServiceName(), msg.getMethodName()});

        rpcServer.getThreadPoolExecutor().submit(new HandlerTask(ctx.channel(), msg, rpcServer));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ServerHandler exception: ", cause);
        ctx.close();
    }

    private static class HandlerTask implements Runnable {
        private final Channel channel;
        private final RequestMessage requestMessage;
        private final RpcServer rpcServer;

        public HandlerTask(Channel channel, RequestMessage requestMessage, RpcServer rpcServer) {
            this.channel = channel;
            this.requestMessage = requestMessage;
            this.rpcServer = rpcServer;
        }

        @Override
        public void run() {
            ResponseMessage responseMessage = new ResponseMessage();
            responseMessage.setMessageID(requestMessage.getMessageID());
            
            try {
                // Step 1: Find service instance
                Object serviceInstance = rpcServer.services.get(requestMessage.getServiceName());
                if (serviceInstance == null) {
                    sendError(responseMessage, ERR_SERVICE_NOT_FOUND, 
                            "Service not found: " + requestMessage.getServiceName());
                    channel.writeAndFlush(responseMessage);
                    return;
                }
                
                // Step 2: Find method by matching parameter types from interface
                Method method = findMatchingMethod(
                        serviceInstance.getClass(),
                        requestMessage.getServiceName(),
                        requestMessage.getMethodName(),
                        requestMessage.getParameters()
                );
                
                if (method == null) {
                    sendError(responseMessage, ERR_METHOD_NOT_FOUND, 
                            buildMethodNotFoundMessage(requestMessage));
                    channel.writeAndFlush(responseMessage);
                    return;
                }
                
                // Step 3: Invoke method
                Object result = method.invoke(serviceInstance, requestMessage.getParameters());
                
                // Step 4: Return success response
                responseMessage.setSuccess(true);
                responseMessage.setResult(result);
                responseMessage.setMessageID(requestMessage.getMessageID());
                
                logger.debug("Method invoked successfully: {}#{}", new Object[]{ 
                        requestMessage.getServiceName(), requestMessage.getMethodName()});
                
            } catch (Exception e) {
                logger.error("Method invocation failed: {}#{}", new Object[]{ 
                        requestMessage.getServiceName(), requestMessage.getMethodName(), e});
                
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                int errorCode = ERR_INVOCATION_FAILED;
                String errorInfo = cause.getMessage();
                
                if (cause instanceof java.util.concurrent.TimeoutException) {
                    errorCode = ERR_TIMEOUT;
                    errorInfo = "Method execution timeout";
                } else if (errorInfo == null || errorInfo.isEmpty()) {
                    errorInfo = cause.getClass().getSimpleName() + ": " + cause.toString();
                }
                
                sendError(responseMessage, errorCode, errorInfo);
            }
            
            channel.writeAndFlush(responseMessage);
        }
        
        /**
         * Find the matching method by name and parameter count.
         * Uses the interface class to get correct parameter types.
         */
        private Method findMatchingMethod(Class<?> implClass, String serviceName, 
                String methodName, Object[] params) {
            int paramCount = params != null ? params.length : 0;
            
            // Try to find method from interface first (to get correct parameter types)
            for (Class<?> iface : implClass.getInterfaces()) {
                if (iface.getName().equals(serviceName)) {
                    for (Method ifaceMethod : iface.getDeclaredMethods()) {
                        if (ifaceMethod.getName().equals(methodName) 
                                && ifaceMethod.getParameterCount() == paramCount) {
                            // Found in interface - use interface's parameter types for exact matching
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
            
            // Fallback: find method by name and param count in impl class
            for (Method m : implClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    m.setAccessible(true);
                    return m;
                }
            }
            
            // Also check public methods from interfaces
            for (Method m : implClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    return m;
                }
            }
            
            return null;
        }
        
        /**
         * Find implementation method with exact parameter types.
         */
        private Method findMethodWithExactParams(Class<?> implClass, String methodName, 
                Class<?>[] paramTypes) {
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
            msg.setMessageID(requestMessage.getMessageID());
        }
    }
}
