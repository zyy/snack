package com.snack.rpc.client;

import com.snack.rpc.codec.client.ClientDecoder;
import com.snack.rpc.codec.client.ClientEncoder;
import com.snack.rpc.codec.client.ClientHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.ChannelPoolHandler;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class ClientChannelPoolHandler implements ChannelPoolHandler {
    @Override
    public void channelReleased(Channel ch) throws Exception {
        // Do nothing because of X and Y
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        // Do nothing because of X and Y
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
        ChannelPipeline channelPipe = ch.pipeline();
        // read
        channelPipe.addLast(new ClientDecoder());
        channelPipe.addLast(new ClientHandler());
        // write
        channelPipe.addLast(new ClientEncoder());
    }
}
