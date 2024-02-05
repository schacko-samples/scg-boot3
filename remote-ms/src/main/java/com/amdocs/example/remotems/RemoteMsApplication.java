package com.amdocs.example.remotems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class RemoteMsApplication {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteMsApplication.class);
    private final RestTemplateBuilder restTemplateBuilder;

    public RemoteMsApplication(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    public static void main(String[] args) {
        SpringApplication.run(RemoteMsApplication.class, args);
    }



    @GetMapping("/headers")
    public List<ResponseEntity<String>> listAllHeaders(@RequestHeader Map<String, String> requestHeaders,
                                                       HttpServletResponse response) {
        HttpHeaders responseHeaders = new HttpHeaders();
        requestHeaders.forEach((key, value) -> {
            LOG.debug(String.format("Header '%s' = %s", key, value));
            responseHeaders.add(key, value);
        });
        response.setHeader("x-dox-traceId", requestHeaders.get("x-b3-traceid"));
        LOG.info("listAllHeaders was called");
        return Collections.singletonList(ResponseEntity.ok()
                .headers(responseHeaders)
                .body(String.format("x-dox-traceId: %s", responseHeaders.get("x-dox-traceId"))));
    }

    @GetMapping("/")
    public ResponseEntity<String> httpbin(@RequestHeader MultiValueMap<String, String> requestHeaders) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpEntity<String> httpEntity = new HttpEntity<>(null, requestHeaders);
        ResponseEntity<String> response = restTemplate.exchange("https://httpbin.org/headers", HttpMethod.GET, httpEntity, String.class);
        return response;
    }

}
