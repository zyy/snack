package com.snack.rpc.codec;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for HeartbeatMessage.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class HeartbeatMessageTest {

    @Test
    public void testPingMessage() {
        HeartbeatMessage ping = HeartbeatMessage.ping("server-1");
        
        assertTrue("Should be ping", ping.isPing());
        assertFalse("Should not be pong", ping.isPong());
        assertEquals("ping", ping.getType());
        assertEquals("server-1", ping.getNodeId());
        assertTrue("Should have timestamp", ping.getTimestamp() > 0);
    }
    
    @Test
    public void testPongMessage() {
        HeartbeatMessage pong = HeartbeatMessage.pong("client-1");
        
        assertFalse("Should not be ping", pong.isPing());
        assertTrue("Should be pong", pong.isPong());
        assertEquals("pong", pong.getType());
        assertEquals("client-1", pong.getNodeId());
    }
    
    @Test
    public void testSettersAndGetters() {
        HeartbeatMessage msg = new HeartbeatMessage();
        
        msg.setType("ping");
        msg.setNodeId("test-node");
        msg.setTimestamp(1234567890L);
        
        assertEquals("ping", msg.getType());
        assertEquals("test-node", msg.getNodeId());
        assertEquals(1234567890L, msg.getTimestamp());
    }
    
    @Test
    public void testStaticFactoryMethods() {
        HeartbeatMessage ping = HeartbeatMessage.ping("node-A");
        HeartbeatMessage pong = HeartbeatMessage.pong("node-B");
        
        assertEquals(HeartbeatMessage.TYPE_PING, ping.getType());
        assertEquals(HeartbeatMessage.TYPE_PONG, pong.getType());
    }
}
