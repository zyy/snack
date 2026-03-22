package com.snack.rpc.codec.server;

import com.snack.rpc.codec.HeartbeatMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Heartbeat pre-decoder: routes heartbeat vs RPC messages.
 * 
 * Detects by peeking first 8 bytes:
 * - "Heartbea..." -> HeartbeatMessage (emitted to pipeline)
 * - "Request "...  -> pass through for ServerDecoder
 * - "Response "..  -> pass through for ClientDecoder
 */
public class HeartbeatRouter extends ByteToMessageDecoder {
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatRouter.class);
    
    private static final byte[] HEARTBEAT_HDR = "Heartbea".getBytes(StandardCharsets.UTF_8);
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 8) {
            return; // need more
        }
        
        int readerIndex = in.readerIndex();
        byte[] magic = new byte[8];
        in.getBytes(readerIndex, magic);
        
        if (isHeartbeatPrefix(magic)) {
            // Find newline
            int nl = indexOf(in, (byte) '\n', readerIndex);
            if (nl < 0) {
                return; // need more
            }
            
            int lineLen = nl - readerIndex + 1;
            ByteBuf lineBuf = in.readBytes(lineLen);
            
            try {
                String line = lineBuf.toString(StandardCharsets.UTF_8).trim();
                String type = line.startsWith("Heartbea:") 
                        ? line.substring("Heartbea:".length()) 
                        : line;
                
                HeartbeatMessage hb = new HeartbeatMessage();
                hb.setType(type.trim());
                hb.setTimestamp(System.currentTimeMillis());
                
                out.add(hb);
                logger.trace("HeartbeatRouter decoded: {}", type);
            } finally {
                lineBuf.release();
            }
        }
        // If not heartbeat, do NOT consume anything - let the next decoder handle it
    }
    
    private boolean isHeartbeatPrefix(byte[] magic) {
        if (magic.length < HEARTBEAT_HDR.length) return false;
        for (int i = 0; i < HEARTBEAT_HDR.length; i++) {
            if (magic[i] != HEARTBEAT_HDR[i]) return false;
        }
        return true;
    }
    
    private int indexOf(ByteBuf buf, byte target, int start) {
        int limit = buf.writerIndex();
        for (int i = start; i < limit; i++) {
            if (buf.getByte(i) == target) {
                return i;
            }
        }
        return -1;
    }
}
