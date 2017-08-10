package com.snack.rpc.codec.server;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class ServerHandler extends SimpleChannelInboundHandler<RequestMessage> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private RpcServer rpcServer;

    public ServerHandler(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RequestMessage msg) throws Exception {
        logger.info("ServerHandler receive message : {}", msg);

        rpcServer.getThreadPoolExecutor().submit(new HandlerTask(ctx.channel(), msg, rpcServer));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ServerHandler error", cause);
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
            try {
                Object[] parameters = requestMessage.getParameters();
                Class[] parameterTypes = new Class[0];
                if (parameters != null) {
                    parameterTypes = new Class[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        parameterTypes[i] = parameters[i].getClass();
                    }
                }

                Object object = rpcServer.services.get(requestMessage.getServiceName());
                Class clazz = object.getClass();
                Method method = clazz.getMethod(requestMessage.getMethodName(), parameterTypes);
                Object result = method.invoke(object, requestMessage.getParameters());

                responseMessage.setSuccess(true);
                responseMessage.setResult(result);
                responseMessage.setMessageID(requestMessage.getMessageID());
            } catch (Exception e) {
                logger.error("HandlerTask error", e);
                responseMessage = ResponseMessage.failure(e);
            }

            channel.writeAndFlush(responseMessage);
        }
    }
}
