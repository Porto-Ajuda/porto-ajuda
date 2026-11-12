package com.portoajuda.aplicacao_osc.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsMaisProperties.class)
public class SmsMaisHttpConfiguration {

    static final String API_BASE_URL = "https://smsmais.com";

    @Bean("smsMaisRestClient")
    RestClient smsMaisRestClient(
            RestClient.Builder builder,
            SmsMaisProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder.clone()
                .baseUrl(API_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }
}
