package com.earnest.video.controller;


import com.earnest.video.entity.BaseVideoEntity;
import com.earnest.video.service.BasicQueryAndPersistenceVideoService;
import com.earnest.video.service.IQiYiAnimationCachedVideoService;
import com.earnest.video.service.IQiYiMovieCachedVideoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/api")
public class VideoController {

    private final Map<String, BasicQueryAndPersistenceVideoService> videoServiceMap;

    public VideoController(IQiYiMovieCachedVideoService movieVideoService, IQiYiAnimationCachedVideoService animationVideoService) {
        Map<String, BasicQueryAndPersistenceVideoService> tempMap = new HashMap<>();
        tempMap.put("movie", movieVideoService);
        tempMap.put("animation", animationVideoService);
        this.videoServiceMap = Collections.unmodifiableMap(tempMap);
    }

    @GetMapping("/{type}")
    public Page<? extends BaseVideoEntity> listAll(@PathVariable String type, Pageable pageRequest) {
        return videoServiceMap.get(type).findAll(pageRequest);
    }


    @GetMapping("/{type}/{id:\\d+}")
    public BaseVideoEntity get(@PathVariable long id,@PathVariable String type) {
        return videoServiceMap.get(type).get(id);
    }

}