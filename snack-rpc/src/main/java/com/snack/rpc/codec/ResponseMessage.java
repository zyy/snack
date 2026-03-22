package com.snack.rpc.codec;

/**
 * RPC response message.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class ResponseMessage {
    private boolean success;
    private Object result;
    private String messageID;
    private String errorInfo;
    private int errorCode;
    private String traceId; // trace ID for cross-node correlation

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }

    public String getMessageID() { return messageID; }
    public void setMessageID(String messageID) { this.messageID = messageID; }

    public String getErrorInfo() { return errorInfo; }
    public void setErrorInfo(String errorInfo) { this.errorInfo = errorInfo; }

    public int getErrorCode() { return errorCode; }
    public void setErrorCode(int errorCode) { this.errorCode = errorCode; }
    
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "success=" + success +
                ", result=" + result +
                ", messageID='" + messageID + '\'' +
                ", errorInfo='" + errorInfo + '\'' +
                ", errorCode=" + errorCode +
                ", traceId='" + traceId + '\'' +
                '}';
    }

    public static ResponseMessage failure(Exception e) {
        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setSuccess(false);
        responseMessage.errorInfo = e.getMessage();
        return responseMessage;
    }
}
