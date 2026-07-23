package com.vectis.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    /**
     * RestTemplate dedicado para llamadas a la API de argentinadatos.com.
     * Timeouts cortos: si la API externa no responde en tiempo, el scheduler
     * captura la excepcion y conserva el ultimo valor conocido.
     */
    @Bean(name = "macroRestTemplate")
    RestTemplate macroRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }
}
