package com.snack.rpc.codec;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */
@Getter
@Setter
public class RequestMessage {
    private String clientName;
    private String serviceName;
    private String methodName;
    private Object[] parameters;
    private String messageID;
    private String serverName;

    @Override
    public String toString() {
        return "RequestMessage{" +
                "clientName='" + clientName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameters=" + Arrays.toString(parameters) +
                ", messageID='" + messageID + '\'' +
                ", serverName='" + serverName + '\'' +
                '}';
    }
}
