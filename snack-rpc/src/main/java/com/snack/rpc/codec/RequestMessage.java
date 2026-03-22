package com.snack.rpc.codec;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import java.util.Arrays;

/**
 * RPC request message.
 */
public class RequestMessage {
    private String clientName;
    private String serviceName;
    private String methodName;
    private Object[] parameters;
    private String messageID;
    private String serverName;
    
    // Trace context for distributed tracing
    private String traceContext; // format: traceId/spanId/parentSpanId/startTime

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public Object[] getParameters() { return parameters; }
    public void setParameters(Object[] parameters) { this.parameters = parameters; }

    public String getMessageID() { return messageID; }
    public void setMessageID(String messageID) { this.messageID = messageID; }

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public String getTraceContext() { return traceContext; }
    public void setTraceContext(String traceContext) { this.traceContext = traceContext; }

    @Override
    public String toString() {
        return "RequestMessage{" +
                "clientName='" + clientName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", parameters=" + Arrays.toString(parameters) +
                ", messageID='" + messageID + '\'' +
                ", serverName='" + serverName + '\'' +
                ", traceContext='" + traceContext + '\'' +
                '}';
    }
}
