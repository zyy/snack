package com.snack.rpc.codec.client;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.serialization.ProtoStuffSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * Client-side encoder for both RequestMessage and HeartbeatMessage.
 */
public class ClientEncoder extends MessageToByteEncoder<Object> {
    private static final byte[] REQUEST_HEADER = "Request ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEARTBEAT_HEADER = "Heartbea".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof RequestMessage) {
            encodeRequest((RequestMessage) msg, out);
        } else if (msg instanceof HeartbeatMessage) {
            encodeHeartbeat((HeartbeatMessage) msg, out);
        }
    }
    
    private void encodeRequest(RequestMessage msg, ByteBuf out) throws Exception {
        // "Request " + [length] + "\r\n" + [body]
        byte[] msgBytes = ProtoStuffSerializer.serializer.serialize(msg);
        out.writeBytes(REQUEST_HEADER);
        out.writeBytes(Integer.toString(msgBytes.length).getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(CRLF);
        out.writeBytes(msgBytes);
    }
    
    private void encodeHeartbeat(HeartbeatMessage msg, ByteBuf out) throws Exception {
        // Simple text protocol for heartbeat: "Heartbea" + type + "\n"
        out.writeBytes(HEARTBEAT_HEADER);
        out.writeByte(':');
        out.writeBytes(msg.getType().getBytes(StandardCharsets.UTF_8));
        out.writeByte('\n');
    }
    
    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof RequestMessage || msg instanceof HeartbeatMessage;
    }
}
