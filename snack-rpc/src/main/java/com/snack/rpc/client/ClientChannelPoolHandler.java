package com.snack.rpc.client;

import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.codec.client.ClientDecoder;
import com.snack.rpc.codec.client.ClientEncoder;
import com.snack.rpc.codec.client.ClientHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Channel pool handler that initializes new channels with heartbeat support.
 * 
 * Features:
 * - Adds IdleStateHandler + heartbeat handler to each pooled channel
 * - Client sends periodic ping to server
 * - Client detects server death via missed pong responses
 */
public class ClientChannelPoolHandler implements ChannelPoolHandler {
    
    // Heartbeat configuration (seconds) - should be shorter than server's reader idle
    private static final int READER_IDLE = 20;  // No response for 20s -> server dead
    private static final int WRITER_IDLE = 5;    // Send ping every 5s
    
    @Override
    public void channelReleased(Channel ch) throws Exception {
        // Channel returned to pool - reset state
        // Don't close, pool will reuse
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        // Channel acquired from pool
    }

    @Override
    public void channelCreated(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 1. Idle detection - fires IdleStateEvent when no read/write for N seconds
        pipeline.addLast("idleStateHandler", new IdleStateHandler(
                READER_IDLE, WRITER_IDLE, 0));
        
        // 2. Heartbeat handler - handles IdleStateEvent and HeartbeatMessage
        pipeline.addLast("clientHeartbeatHandler", 
                new ClientHeartbeatHandler("client-" + ch.hashCode()));
        
        // 3. RPC codec
        pipeline.addLast("clientEncoder", new ClientEncoder());
        pipeline.addLast("clientDecoder", new ClientDecoder());
        pipeline.addLast("clientHandler", new ClientHandler());
    }
}
