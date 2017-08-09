package com.snack.rpc.codec.client;

import com.snack.rpc.client.RpcClientChannel;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class ClientHandler extends SimpleChannelInboundHandler<ResponseMessage> {
    private Object result;
    private volatile Channel channel;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResponseMessage msg) throws Exception {
        System.out.println(msg.toString());
        RpcClientChannel channel = (RpcClientChannel) ctx.channel();
        channel.set(msg);
    }

    public Object getResult() {
        return result;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        channel = ctx.channel();
    }

    public void doRequest(RequestMessage requestMessage) {
        channel.writeAndFlush(requestMessage);
    }
}
