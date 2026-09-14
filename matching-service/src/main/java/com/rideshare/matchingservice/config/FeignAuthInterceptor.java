package com.rideshare.matchingservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignAuthInterceptor implements RequestInterceptor {

    @Value("${location.service.token}")
    private String serviceToken;

    @Override
    public void apply(RequestTemplate template) {
        template.header(
                "Authorization",
                "Bearer " + serviceToken
        );
    }
}
