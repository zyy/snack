package com.snack.rpc.client;

import com.snack.rpc.codec.HeartbeatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side heartbeat handler.
 * 
 * Features:
 * - Sends periodic heartbeat pings to server (on WRITER_IDLE)
 * - Detects server disconnection via missed pong responses (on READER_IDLE)
 * - Tracks heartbeat failures for connection health monitoring
 */
public class ClientHeartbeatHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ClientHeartbeatHandler.class);
    
    private final String clientId;
    private final AtomicInteger missedHeartbeats = new AtomicInteger(0);
    private static final int MAX_MISSED_HEARTBEATS = 3;
    
    // Heartbeat config (must match ClientChannelPoolHandler)
    private static final int READER_IDLE = 20;
    private static final int WRITER_IDLE = 5;
    
    public ClientHeartbeatHandler(String clientId) {
        this.clientId = clientId;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HeartbeatMessage) {
            handleHeartbeat(ctx, (HeartbeatMessage) msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            IdleState state = idleEvent.state();
            
            if (state == IdleState.WRITER_IDLE) {
                HeartbeatMessage ping = HeartbeatMessage.ping(clientId);
                ctx.writeAndFlush(ping);
                logger.debug("Write idle, sent heartbeat ping to {}", new Object[]{ctx.channel()});
                
            } else if (state == IdleState.READER_IDLE) {
                int missed = missedHeartbeats.incrementAndGet();
                logger.warn("No message received for {}s, missed heartbeats: {}/{}", 
                        new Object[]{READER_IDLE, missed, MAX_MISSED_HEARTBEATS});
                
                if (missed >= MAX_MISSED_HEARTBEATS) {
                    logger.error("Too many missed heartbeats ({}), closing connection", new Object[]{missed});
                    ctx.close();
                }
            }
        }
        super.userEventTriggered(ctx, evt);
    }
    
    private void handleHeartbeat(ChannelHandlerContext ctx, HeartbeatMessage msg) {
        if (msg.isPong()) {
            int missed = missedHeartbeats.getAndSet(0);
            if (missed > 0) {
                logger.info("Server responded to heartbeat, connection restored (missed={})", new Object[]{missed});
            } else {
                logger.trace("Heartbeat pong received, connection healthy");
            }
        } else if (msg.isPing()) {
            HeartbeatMessage pong = HeartbeatMessage.pong(clientId);
            ctx.writeAndFlush(pong);
            logger.debug("Heartbeat ping from server, replied with pong");
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client channel active: {}", new Object[]{ctx.channel()});
        missedHeartbeats.set(0);
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("Client channel inactive: {}", new Object[]{ctx.channel()});
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ClientHeartbeatHandler exception: ", cause);
        ctx.close();
    }
    
    public boolean isHealthy() {
        return missedHeartbeats.get() < MAX_MISSED_HEARTBEATS;
    }
    
    public int getMissedHeartbeats() {
        return missedHeartbeats.get();
    }
}
