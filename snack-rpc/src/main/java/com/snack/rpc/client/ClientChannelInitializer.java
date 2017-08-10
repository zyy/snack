package com.snack.rpc.client;

import com.snack.rpc.codec.client.ClientDecoder;
import com.snack.rpc.codec.client.ClientEncoder;
import com.snack.rpc.codec.client.ClientHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline channelPipe = ch.pipeline();
        // read
        channelPipe.addLast(new ClientDecoder());
        channelPipe.addLast(new ClientHandler());
        // write
        channelPipe.addLast(new ClientEncoder());
    }
}
