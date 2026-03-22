package com.snack.rpc.codec.server;

import com.snack.rpc.codec.HeartbeatMessage;
import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.serialization.ProtoStuffSerializer;
import com.snack.rpc.serialization.SerializerManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * Server-side encoder for both ResponseMessage and HeartbeatMessage.
 */
public class ServerEncoder extends MessageToByteEncoder<Object> {
    private static final byte[] RESPONSE_HEADER = "Response ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HEARTBEAT_HEADER = "Heartbea".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof ResponseMessage) {
            encodeResponse((ResponseMessage) msg, out);
        } else if (msg instanceof HeartbeatMessage) {
            encodeHeartbeat((HeartbeatMessage) msg, out);
        }
    }
    
    private void encodeResponse(ResponseMessage msg, ByteBuf out) throws Exception {
        // "Response " + [length] + "\r\n" + [body]
        byte[] msgBytes = SerializerManager.getInstance().serialize(msg);
        out.writeBytes(RESPONSE_HEADER);
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
        return msg instanceof ResponseMessage || msg instanceof HeartbeatMessage;
    }
}
