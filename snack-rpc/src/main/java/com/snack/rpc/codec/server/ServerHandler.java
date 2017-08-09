package com.snack.rpc.codec.server;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.lang.reflect.Method;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class ServerHandler extends SimpleChannelInboundHandler<RequestMessage> {
    private RpcServer rpcServer;

    public ServerHandler(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RequestMessage msg) throws Exception {
        System.out.println(msg.toString());

        Object[] parameters = msg.getParameters();
        Class[] parameterTypes = new Class[0];
        if (parameters != null) {
            parameterTypes = new Class[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                parameterTypes[i] = parameters[i].getClass();
            }
        }

        Object obj = rpcServer.services.get(msg.getServiceName());
        Class clazz = obj.getClass();
        Method method = clazz.getMethod(msg.getMethodName(), parameterTypes);
        Object result = method.invoke(obj, msg.getParameters());

        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setSuccess(true);
        responseMessage.setResult(result);
        responseMessage.setMessageID(msg.getMessageID());

        ctx.channel().writeAndFlush(responseMessage);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
