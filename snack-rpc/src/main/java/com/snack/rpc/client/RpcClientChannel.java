package com.snack.rpc.client;

import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RPC Client Channel - handles synchronous response retrieval with timeout support.
 * 
 * Bug fix: Returns ResponseMessage (may be null on timeout).
 * The caller MUST check for null and handle timeout appropriately.
 */
public class RpcClientChannel extends NioSocketChannel {
    private ResponseMessage responseMessage;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasMsg = lock.newCondition();

    public void reset() {
        lock.lock();
        try {
            this.responseMessage = null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait for response with timeout.
     * 
     * @param timeout timeout in milliseconds
     * @return ResponseMessage if received, null if timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public ResponseMessage get(long timeout) throws InterruptedException {
        lock.lock();
        try {
            long end = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (responseMessage == null) {
                boolean ok = hasMsg.await(remaining, TimeUnit.MILLISECONDS);
                if (ok) {
                    // signaled - response may be set
                    break;
                }
                // timeout expired
                remaining = end - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
            }
            return responseMessage;
        } finally {
            lock.unlock();
        }
    }

    public void set(ResponseMessage message) {
        lock.lock();
        try {
            this.responseMessage = message;
            hasMsg.signal();
        } finally {
            lock.unlock();
        }
    }
}
