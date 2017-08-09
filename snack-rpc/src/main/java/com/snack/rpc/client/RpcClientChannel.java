package com.snack.rpc.client;

import com.snack.rpc.codec.ResponseMessage;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by yangyang.zhao on 2017/8/9.
 */
public class RpcClientChannel extends NioSocketChannel {
    private ResponseMessage responseMessage;
    private ReentrantLock lock = new ReentrantLock();
    private Condition hasMsg = lock.newCondition();

    public void reset() {
        this.responseMessage = null;
    }

    public ResponseMessage get(long timeout) throws InterruptedException {
        lock.lock();
        try {
            long end = System.currentTimeMillis() + timeout;
            long time = timeout;
            while (responseMessage == null) {
                boolean ok = hasMsg.await(time, TimeUnit.MILLISECONDS);
                if (ok || (time = end - System.currentTimeMillis()) <= 0) {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        return responseMessage;
    }

    public void set(ResponseMessage message) {
        lock.lock();
        try {
            responseMessage = message;
            hasMsg.signal();
        } finally {
            lock.unlock();
        }
    }
}
