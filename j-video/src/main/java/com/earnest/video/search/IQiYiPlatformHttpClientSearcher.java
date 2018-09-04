package com.earnest.video.search;

import com.alibaba.fastjson.JSONObject;
import com.earnest.crawler.core.Browser;
import com.earnest.video.entity.IQiYi;
import com.earnest.video.exception.UnknownException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
public class IQiYiPlatformHttpClientSearcher implements PlatformSearcher<IQiYi> {

    private final HttpClient httpClient;

    private static final String URL_STRING = "http://search.video.iqiyi.com/o?channel_name=&if=html5&pageNum=${pageNum}&pageSize=${pageSize}&limit=20&timeLength=0&releaseDate=&key=${keyword}start=2&threeCategory=&u=cupy97yt8x8fb6vy67k4yyo&qyid=cupy97yt8x8fb6vy67k4yyo&pu=&video_allow_3rd=1&intent_result_number=10&intent_category_type=1&vfrm=2-3-0-1&_=${date}";

    private final BasicResponseHandler responseHandler;

    private final RequestBuilder requestBuilder =
            RequestBuilder.get("http://search.video.iqiyi.com/o")
                    .setHeader(Browser.USER_AGENT, Browser.IPHONE_X.userAgent())
                    .addParameter("if", "html5")
                    .addParameter("timeLength", "0")
                    .addParameter("video_allow_3rd", "1")
                    .addParameter("intent_result_number", "10")
                    .addParameter("intent_category_type", "1");


    @Override
    public Page<IQiYi> search(String keyword, Pageable pageRequest) throws IOException {
        Assert.hasText(keyword, "keyword is empty or null");
        HttpUriRequest httpUriRequest = requestBuilder.addParameter("key", keyword).build();

        String result = httpClient.execute(httpUriRequest, responseHandler);

        JSONObject resultJsonObject = JSONObject.parseObject(result);

        String code = resultJsonObject.getString("code");
        if (!StringUtils.equals(code, "A00000")) {
            throw new UnknownException(String.format("An unknown error occurred while connecting to iQiYi:%s,code:%s", httpUriRequest.getRequestLine().getUri(), code));
        }

        JSONObject dataJsonObject = resultJsonObject.getJSONObject("data");

        List<IQiYi> resultCollections = dataJsonObject.getJSONArray("docinfos")
                .stream()
                .map(e -> (JSONObject) e)
                .map(jsonObject -> {
                    IQiYi iQiYi = new IQiYi();
                    JSONObject albumDocInfo = jsonObject.getJSONObject("albumDocInfo");

                    iQiYi.setTitle(albumDocInfo.getString("albumTitle"));

                    return iQiYi;

                }).collect(Collectors.toList());


        return new PageImpl<>(resultCollections, pageRequest, dataJsonObject.getIntValue("result_num"));
    }

    public static void main(String[] args) throws IOException {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setProxy(new HttpHost("206.189.65.241", 8080))
                .build();

        HttpGet httpGet = new HttpGet("http://www.baidu.com");

        CloseableHttpResponse closeableHttpResponse = httpClient.execute(httpGet);



        String execute = httpClient.execute(httpGet, new BasicResponseHandler(){
            @Override
            public String handleEntity(HttpEntity entity) throws IOException {
                return EntityUtils.toString(entity, Charset.defaultCharset().displayName());
            }
        });

        System.out.println(execute);


    }
}
