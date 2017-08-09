package com.snack.rpc.codec.client;

import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.serialization.ProtoStuffSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ClientEncoder extends MessageToByteEncoder<RequestMessage> {
    private static final byte[] REQUEST_HEADER = "Request ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    @Override
    protected void encode(ChannelHandlerContext ctx, RequestMessage msg, ByteBuf out) throws Exception {
        // "Request " + [length] + "\r\n" + [body]
        byte[] msgBytes = ProtoStuffSerializer.serializer.serialize(msg);
        out.writeBytes(REQUEST_HEADER);
        out.writeBytes(Integer.toString(msgBytes.length).getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(CRLF);
        out.writeBytes(msgBytes);
    }
}
