package com.snack.rpc.server;

import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.server.ServerDecoder;
import com.snack.rpc.codec.server.ServerEncoder;
import com.snack.rpc.codec.server.ServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private RpcServer rpcServer;

    public ServerChannelInitializer(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipe = ch.pipeline();
        // read
        channelPipe.addLast(new ServerDecoder());
        channelPipe.addLast(new ServerHandler(rpcServer));
        // write
        channelPipe.addLast(new ServerEncoder());
    }
}
