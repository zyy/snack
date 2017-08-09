package com.snack.rpc.codec;

import com.snack.rpc.serialization.ProtoStuffSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class Decoder<T> extends ByteToMessageDecoder {
    private Class<T> clazz;
    private byte[] header;
    private int headerLength;
    private Position position = Position.HEADER;
    private int length;

    public Decoder(byte[] header, Class<T> clazz) {
        this.header = header;
        this.headerLength = header.length;
        this.clazz = clazz;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decode(in, out);
    }

    private void decode(ByteBuf in, List<Object> out) {
        // "Request/Response " + [length] + "\r\n" + [body]
        switch (position) {
            case HEADER:
                decodeHeader(in, out);
                break;
            case LENGTH:
                decodeLength(in, out);
                break;
            case CRLF:
                decodeCRLF(in, out);
                break;
            case BODY:
                decodeBody(in, out);
                break;
            default:
                throw new RuntimeException("invalid parse position: " + position);
        }
    }

    private void decodeBody(ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= length) {
            byte[] body = new byte[length];
            in.readBytes(body);
            Object msg = ProtoStuffSerializer.serializer.deserialize(body, clazz);
            out.add(msg);
            System.out.println(msg);

            position = Position.HEADER;
        }
    }

    private void decodeCRLF(ByteBuf in, List<Object> out) {
        if (in.readableBytes() > 2) {
            if (in.readByte() != '\r' || in.readByte() != '\n') {
                throw new RuntimeException("expect: \\r\\n");
            }
            position = Position.BODY;
            decode(in, out);
        }
    }

    private void decodeLength(ByteBuf in, List<Object> out) {
        if (in.readableBytes() > 0) {
            int length = in.bytesBefore((byte) '\r');
            if (length != -1) {
                byte[] bytes = new byte[length];
                in.readBytes(bytes);
                this.length = Integer.parseInt(new String(bytes, StandardCharsets.US_ASCII));
                position = Position.CRLF;
                decode(in, out);
            }
        }
    }

    private void decodeHeader(ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= headerLength) {
            byte[] header = new byte[headerLength];
            in.readBytes(header);
            if (!Arrays.equals(this.header, header)) {
                throw new RuntimeException("expect: " + new String(header, StandardCharsets.US_ASCII));
            }
            position = Position.LENGTH;
            decode(in, out);
        }
    }

    private enum Position {
        HEADER, LENGTH, CRLF, BODY
    }
}
