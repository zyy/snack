package com.snack.rpc.client;

import com.snack.rpc.codec.RequestMessage;
import com.snack.rpc.codec.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Future-based async RPC call result.
 * Provides both blocking and non-blocking ways to get the result.
 * Created by yangyang.zhao on 2017/8/8.
 */
public class RpcFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RpcFuture.class);
    
    private final CountDownLatch latch = new CountDownLatch(1);
    private final long startTime = System.currentTimeMillis();
    private final long timeout;
    
    private volatile ResponseMessage responseMessage;
    private volatile Exception exception;
    
    public RpcFuture(long timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Set the response when RPC call completes.
     */
    public void setResponse(ResponseMessage response) {
        this.responseMessage = response;
        latch.countDown();
    }
    
    /**
     * Set the exception when RPC call fails.
     */
    public void setException(Exception e) {
        this.exception = e;
        latch.countDown();
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }
    
    @Override
    public boolean isCancelled() {
        return false;
    }
    
    @Override
    public boolean isDone() {
        return latch.getCount() == 0;
    }
    
    @Override
    public Object get() throws InterruptedException, ExecutionException {
        latch.await();
        return getResult();
    }
    
    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean ok = latch.await(timeout, unit);
        if (!ok) {
            throw new TimeoutException("RPC call timeout after " + timeout + " " + unit);
        }
        return getResult();
    }
    
    private Object getResult() throws ExecutionException {
        if (exception != null) {
            throw new ExecutionException(exception);
        }
        if (responseMessage == null) {
            throw new ExecutionException(new RuntimeException("RPC response is null (timeout?)"));
        }
        if (!responseMessage.isSuccess()) {
            RuntimeException e = new RuntimeException(
                    "RPC call failed: " + responseMessage.getErrorInfo());
            throw new ExecutionException(e);
        }
        return responseMessage.getResult();
    }
    
    /**
     * Get the elapsed time since the call started.
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Get the timeout configured for this call.
     */
    public long getTimeout() {
        return timeout;
    }
    
    /**
     * Add a callback to be executed when the call completes.
     */
    public RpcFuture addCallback(RpcCallback callback) {
        CompletableFuture.runAsync(() -> {
            try {
                Object result = get();
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
        return this;
    }
    
    /**
     * Callback interface for async RPC calls.
     */
    public interface RpcCallback {
        void onSuccess(Object result);
        void onFailure(Exception e);
    }
}
