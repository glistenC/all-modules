package com.earnest.video.core.search;

import com.alibaba.fastjson.util.IOUtils;
import com.earnest.crawler.core.proxy.HttpProxyPool;
import com.earnest.video.entity.BaseVideoEntity;
import com.earnest.video.entity.Platform;
import com.earnest.video.exception.UnsupportedPlatformException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class DefaultPlatformSearcherManager implements PlatformSearcherManager {

    private final Map<Platform, PlatformSearcher<? extends BaseVideoEntity>> platformSearcherMap = new LinkedHashMap<>(5);

    @Getter
    @Setter
    private Executor executor;

    @Getter
    @Setter
    private int ignoreSecondTimeOut = 5; //忽略多少秒后的结果


    /**
     * 并发搜索每个平台，将获取到的内容进行归总。
     *
     * @param keyword
     * @param pageRequest
     * @return
     * @throws IOException
     */
    @Override
    public Page<BaseVideoEntity> search(String keyword, Pageable pageRequest) throws IOException {


        List<? extends Page<? extends BaseVideoEntity>> results = platformSearcherMap.values()
                .stream()
                .map(search -> (Callable<Page<? extends BaseVideoEntity>>) () -> search.search(keyword, pageRequest))
                .map(FutureTask::new)
                .peek(f -> executor.execute(f)) //执行
                .map(futureGetIgnoreError())//取结果
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        //收集
        long totalElements = 0L;

        List<BaseVideoEntity> content = new ArrayList<>(pageRequest.getPageSize() * results.size());


        for (Page<? extends BaseVideoEntity> baseVideoEntities : results) {
            totalElements += baseVideoEntities.getTotalElements();
            content.addAll(baseVideoEntities.getContent());
        }


        return new PageImpl<>(content, pageRequest, totalElements);
    }


    @Override
    public Platform getPlatform() {
        return null;
    }

    private Function<Future<Page<? extends BaseVideoEntity>>, ? extends Page<? extends BaseVideoEntity>> futureGetIgnoreError() {
        return f -> {
            try {
                return f.get(ignoreSecondTimeOut, TimeUnit.SECONDS);
            } catch (Exception e) { //发生错误时忽略
                if (log.isDebugEnabled()) {
                    if (e instanceof TimeoutException) {
                        log.debug("A task timed out has been ignored,error:{}", e.getMessage());
                    }
                }
                return null;
            }
        };
    }


    @Override
    @SuppressWarnings("unchecked")
    public Page<BaseVideoEntity> search(String keyword, Pageable pageRequest, Platform platform) throws IOException {

        if (platform == null) {
            return search(keyword, pageRequest);
        }

        PlatformSearcher<? extends BaseVideoEntity> platformSearcher = platformSearcherMap.get(platform);

        if (platformSearcher != null) {
            return (Page<BaseVideoEntity>) platformSearcher.search(keyword, pageRequest);
        }

        throw new UnsupportedPlatformException(platform + " Platform does not support or is not specified");
    }


    @Override
    public void close() {
        try {
            platformSearcherMap.values()
                    .forEach(IOUtils::close);
        } finally {
            if (executor instanceof ExecutorService) {
                ((ExecutorService) executor).shutdown();
            }

        }
    }

    @Override
    public void setHttpProxyPool(HttpProxyPool httpProxyPool) {
        platformSearcherMap.values().forEach(s -> s.setHttpProxyPool(httpProxyPool));
    }


    @Override
    public void addWork(PlatformSearcher<? extends BaseVideoEntity> platformSearcher) {
        Assert.notNull(platformSearcher, "platformSearcher is null");
        platformSearcherMap.put(platformSearcher.getPlatform(), platformSearcher);
    }


}
