package com.snack.rpc.codec;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
@Getter
@Setter
public class ResponseMessage {
    private boolean success;
    private Object result;
    private String messageID;
    private String errorInfo;
    private int errorCode;

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "success=" + success +
                ", result=" + result +
                ", messageID='" + messageID + '\'' +
                ", errorInfo='" + errorInfo + '\'' +
                ", errorCode=" + errorCode +
                '}';
    }
}
