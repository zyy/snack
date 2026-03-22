package com.snack.rpc.codec;

/**
 * Created by yangyang.zhao on 2017/8/8.
 */




import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Heartbeat message for keepalive between client and server.
 * Used to detect dead connections and trigger reconnection.
 */
@JsonRootName("heartbeat")
public class HeartbeatMessage {
    
    public static final String TYPE_PING = "ping";
    public static final String TYPE_PONG = "pong";
    
    private String type;
    private long timestamp;
    private String nodeId;
    
    public HeartbeatMessage() {
    }
    
    public HeartbeatMessage(String type, String nodeId) {
        this.type = type;
        this.nodeId = nodeId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static HeartbeatMessage ping(String nodeId) {
        return new HeartbeatMessage(TYPE_PING, nodeId);
    }
    
    public static HeartbeatMessage pong(String nodeId) {
        return new HeartbeatMessage(TYPE_PONG, nodeId);
    }
    
    public boolean isPing() {
        return TYPE_PING.equals(type);
    }
    
    public boolean isPong() {
        return TYPE_PONG.equals(type);
    }
    
    // Getters and setters (manual, no Lombok to avoid annotation processor issues)
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
}
