package com.alibaba.otter.shared.communication.core.impl;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.alibaba.otter.shared.common.utils.thread.NamedThreadFactory;
import com.alibaba.otter.shared.communication.core.CommunicationClient;
import com.alibaba.otter.shared.communication.core.CommunicationEndpoint;
import com.alibaba.otter.shared.communication.core.exception.CommunicationException;
import com.alibaba.otter.shared.communication.core.model.Callback;
import com.alibaba.otter.shared.communication.core.model.Event;

public class InternalCommunicationClientImpl implements CommunicationClient {

    private static final Logger            logger     = LoggerFactory.getLogger(DefaultCommunicationClientImpl.class);

    private CommunicationEndpoint endPoint    = null;
    private int                            poolSize   = 10;
    private ExecutorService                executor   = null;
    private int                            retry      = 3;
    private int                            retryDelay = 1000;
    private boolean                        discard    = false;

    public InternalCommunicationClientImpl(){
    }



    public void initial() {
        RejectedExecutionHandler handler = null;
        if (discard) {
            handler = new ThreadPoolExecutor.DiscardPolicy();
        } else {
            handler = new ThreadPoolExecutor.AbortPolicy();
        }

        executor = new ThreadPoolExecutor(poolSize, poolSize, 60 * 1000L, TimeUnit.MILLISECONDS,
                                          new LinkedBlockingQueue<Runnable>(10 * 1000),
                                          new NamedThreadFactory("communication-async"), handler);
    }

    public void destory() {
        executor.shutdown();
    }

    public Object call(final String addr, final Event event) {
        int count = 0;
        Throwable ex = null;
        while (count++ < retry) {
            try {
                return endPoint.acceptEvent(event);
            } catch (Exception e) {
                logger.error(String.format("call[%s] , retry[%s]", addr, count), e);
                try {
                    Thread.sleep(count * retryDelay);
                } catch (InterruptedException e1) {
                    // ignore
                }
                ex = e;
            } 
        }

        logger.error("call[{}] failed , event[{}]!", addr, event.toString());
        throw new CommunicationException("call[" + addr + "] , Event[" + event.toString() + "]", ex);
    }

    public void call(final String addr, final Event event, final Callback callback) {
       
        submit(new Runnable() {

            @Override
            public void run() {
                Object obj = call(addr, event);
                callback.call(obj);
            }
        });
    }

    public Object call(final String[] addrs, final Event event) {
        
        if (addrs == null || addrs.length == 0) {
            throw new IllegalArgumentException("addrs example: 127.0.0.1:1099");
        }

        ExecutorCompletionService completionService = new ExecutorCompletionService(executor);
        List<Future<Object>> futures = new ArrayList<Future<Object>>(addrs.length);
        List result = new ArrayList(10);
        for (final String addr : addrs) {
            futures.add(completionService.submit((new Callable<Object>() {

                @Override
                public Object call() throws Exception {
                    return InternalCommunicationClientImpl.this.call(addr, event);
                }
            })));
        }

        Exception ex = null;
        int errorIndex = 0;
        while (errorIndex < futures.size()) {
            try {
                Future future = completionService.take();// 它也可能被打断
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ex = e;
                break;
            } catch (ExecutionException e) {
                ex = e;
                break;
            }

            errorIndex++;
        }

        if (errorIndex < futures.size()) {
            for (int index = 0; index < futures.size(); index++) {
                Future<Object> future = futures.get(index);
                if (future.isDone() == false) {
                    future.cancel(true);
                }
            }
        } else {
            for (int index = 0; index < futures.size(); index++) {
                Future<Object> future = futures.get(index);
                try {
                    result.add(future.get());
                } catch (InterruptedException e) {
                    // ignore
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    // ignore
                }
            }
        }

        if (ex != null) {
            throw new CommunicationException(String.format("call addr[%s] error by %s", addrs[errorIndex],
                                                           ex.getMessage()), ex);
        } else {
            return result;
        }
    }

    public void call(final String[] addrs, final Event event, final Callback callback) {
        if (addrs == null || addrs.length == 0) {
            throw new IllegalArgumentException("addrs example: 127.0.0.1:1099");
        }
        submit(new Runnable() {

            @Override
            public void run() {
                Object obj = call(addrs, event);
                callback.call(obj);
            }
        });
    }

    /**
     * 直接提交一个异步任务
     */
    public Future submit(Runnable call) {
       
        return executor.submit(call);
    }

    /**
     * 直接提交一个异步任务
     */
    public Future submit(Callable call) {
        
        return executor.submit(call);
    }

    // ===================== helper method ==================


    // ============================= setter / getter ==========================

    public void setEndPoint(CommunicationEndpoint connection ) {
        this.endPoint = connection;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

}