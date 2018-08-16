package com.earnest.crawler.core.spider;


import com.alibaba.fastjson.util.IOUtils;
import com.earnest.crawler.core.StringResponseResult;
import com.earnest.crawler.core.downloader.Downloader;
import com.earnest.crawler.core.exception.TakeTimeoutException;
import com.earnest.crawler.core.extractor.HttpRequestExtractor;
import com.earnest.crawler.core.pipeline.Pipeline;
import com.earnest.crawler.core.scheduler.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpUriRequest;
import org.springframework.util.Assert;
import org.springframework.util.CustomizableThreadCreator;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
public class DefaultSpider implements Spider {

    private final Downloader downloader;
    private final Scheduler scheduler;
    private final HttpRequestExtractor httpRequestExtractor;
    private final Pipeline pipeline;
    private final ThreadPoolExecutor threadPool;

    public DefaultSpider(Downloader downloader, Scheduler scheduler, HttpRequestExtractor httpRequestExtractor, Pipeline pipeline, Integer threadNumber) {
        Assert.notNull(downloader, "downloader is null");
        Assert.notNull(scheduler, "scheduler is null");
        Assert.notNull(pipeline, "custom is null");
        this.downloader = downloader;
        this.scheduler = scheduler;
        this.httpRequestExtractor = httpRequestExtractor;
        this.pipeline = pipeline;
        this.threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadNumber, new SpiderThreadFactory());

    }


    @Override
    public void start() {

        int threadNumber = threadPool.getMaximumPoolSize();
        BlockingQueue<StringResponseResult> responseResultsQueue = new ArrayBlockingQueue<>(1000);

        Thread mainThread = Thread.currentThread();

        //进行下载
        for (int i = 0; i < threadNumber; i++) {
            threadPool.execute(() -> {
                HttpUriRequest httpUriRequest;
                while (true) {
                    try {
                        httpUriRequest = scheduler.take();
                    } catch (TakeTimeoutException e) {
                        break;
                    }

                    if (httpUriRequest != null) {
                        StringResponseResult responseResult = downloader.download(httpUriRequest);
                        //放入请求
                        try {
                            responseResultsQueue.put(responseResult);
                        } catch (InterruptedException e) {
                            log.error("Interrupted when placing {}", responseResult);
                        }
                    } else
                        break; //拿到null时
                }
                if (threadPool.getActiveCount() == 1) {//当活动线程只剩下最后一个时
                    mainThread.interrupt();
                }

            });
        }
        //对结果进行处理
        handleResult(responseResultsQueue);
    }

    /**
     * 利用主线程对返回结果进行处理。
     *
     * @param responseResultsQueue
     */
    private void handleResult(BlockingQueue<StringResponseResult> responseResultsQueue) {
        while (true) {
            try {
                //当有活动的线程才去取值
                if (threadPool.getActiveCount() != 0) {
                    //获取下载的结果
                    StringResponseResult stringResponseResult = responseResultsQueue.take();
                    //对下载的结果进行提取
                    Set<HttpUriRequest> httpUriRequests = httpRequestExtractor.extract(stringResponseResult);
                    //将结果放入
                    scheduler.putAll(httpUriRequests);
                    //将结果进行处理
                    pipeline.pipe(stringResponseResult);
                } else
                    break;
            } catch (InterruptedException e) {//在线程结果处理完成后发生
                break;
            }
        }

        log.info("download completed, exit...");

    }

    @Override
    public void shutdown() {
        threadPool.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return threadPool.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return threadPool.getActiveCount() != 0;
    }


    @Override
    public void close() throws IOException {
        if (!threadPool.isShutdown()) {
            threadPool.shutdown();
        }
        closeComponents(downloader, scheduler, httpRequestExtractor, pipeline);

    }

    private void closeComponents(Object... components) {
        Arrays.stream(components)
                .filter(a -> a instanceof Closeable)
                .map(s -> (Closeable) s)
                .forEach(IOUtils::close);
    }


    private class SpiderThreadFactory extends CustomizableThreadCreator implements ThreadFactory {
        final static String DEFAULT_THREAD_GROUP_NAME = "spider";
        final static String DEFAULT_THREAD__NAME_PREFIX = "crawler";

        public SpiderThreadFactory() {
            this(DEFAULT_THREAD__NAME_PREFIX);
        }

        public SpiderThreadFactory(String threadNamePrefix) {
            super(threadNamePrefix);
            setThreadGroupName(DEFAULT_THREAD_GROUP_NAME);
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return createThread(runnable);
        }
    }
}
