package com.snack.rpc.codec.client;

import com.snack.rpc.client.RpcClientChannel;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class ClientHandler extends SimpleChannelInboundHandler<ResponseMessage> {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private volatile Channel channel;
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResponseMessage msg) throws Exception {
        logger.info(msg.toString());
        RpcClientChannel channel = (RpcClientChannel) ctx.channel();
        channel.set(msg);
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
