package com.snack.rpc.codec.server;

import com.snack.rpc.codec.Decoder;
import com.snack.rpc.codec.RequestMessage;

import java.nio.charset.StandardCharsets;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ServerDecoder extends Decoder<RequestMessage> {
    // "Request " + [length] + "\r\n" + [body]
    private static final byte[] REQUEST_HEADER = "Request ".getBytes(StandardCharsets.US_ASCII);

    public ServerDecoder() {
        super(REQUEST_HEADER, RequestMessage.class);
    }
}
