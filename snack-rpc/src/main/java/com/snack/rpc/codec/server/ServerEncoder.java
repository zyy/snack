package com.snack.rpc.codec.server;

import com.snack.rpc.codec.ResponseMessage;
import com.snack.rpc.serialization.ProtoStuffSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ServerEncoder extends MessageToByteEncoder<ResponseMessage> {
    private static final byte[] RESPONSE_HEADER = "Response ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseMessage msg, ByteBuf out) throws Exception {
        // "Response " + [length] + "\r\n" + [body]
        byte[] msgBytes = ProtoStuffSerializer.serializer.serialize(msg);
        out.writeBytes(RESPONSE_HEADER);
        out.writeBytes(Integer.toString(msgBytes.length).getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(CRLF);
        out.writeBytes(msgBytes);
    }
}
