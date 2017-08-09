package com.snack.rpc.codec.client;

import com.snack.rpc.codec.Decoder;
import com.snack.rpc.codec.ResponseMessage;

import java.nio.charset.StandardCharsets;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ClientDecoder extends Decoder<ResponseMessage> {
    // "Response " + [length] + "\r\n" + [body]
    private static final byte[] RESPONSE_HEADER = "Response ".getBytes(StandardCharsets.US_ASCII);

    public ClientDecoder() {
        super(RESPONSE_HEADER, ResponseMessage.class);
    }
}
