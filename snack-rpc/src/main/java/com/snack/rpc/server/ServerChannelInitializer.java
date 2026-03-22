package com.snack.rpc.server;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import com.snack.rpc.RpcServer;
import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.codec.server.HeartbeatRouter;
import com.snack.rpc.codec.server.ServerDecoder;
import com.snack.rpc.codec.server.ServerEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Server channel initializer with heartbeat support.
 * 
 * Pipeline:
 * 1. IdleStateHandler         -> fires IdleStateEvent when channel is idle
 * 2. HeartbeatRouter          -> routes: HeartbeatMessage -> pass to handler, else -> pass through
 * 3. ServerEncoder           -> encodes ResponseMessage + HeartbeatMessage outbound
 * 4. ServerDecoder           -> decodes RequestMessage inbound
 * 5. ServerHandler           -> processes RequestMessage + IdleStateEvent inbound
 */
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
    private RpcServer rpcServer;
    
    // Heartbeat configuration (seconds)
    private static final int READER_IDLE = 30;  // close if no read for 30s
    private static final int WRITER_IDLE = 10;  // send ping if no write for 10s
    
    public ServerChannelInitializer(RpcServer rpcServer) {
        this.rpcServer = rpcServer;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline p = ch.pipeline();
        
        // 1. Detect idle and fire IdleStateEvent
        p.addLast("idleStateHandler", new IdleStateHandler(READER_IDLE, WRITER_IDLE, 0));
        
        // 2. Heartbeat routing (must be before decoder)
        p.addLast("heartbeatRouter", new HeartbeatRouter());
        
        // 3. RPC codec
        p.addLast("serverEncoder", new ServerEncoder());
        p.addLast("serverDecoder", new ServerDecoder());
        
        // 4. Business logic - handles both RPC and heartbeat
        p.addLast("serverHandler", new ServerHandler(rpcServer));
    }
}
