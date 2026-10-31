package com.valle.boleteria.sales.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Cliente REST hacia catalog-service. Decisión cerrada: RestClient de Spring
 * (incluido en Boot 3), sin Feign ni Spring Cloud — cero dependencias extra.
 * El RestClient se declara como bean para poder sustituirlo en los tests por un
 * MockRestServiceServer (ver {@code VentaFlujoTest.CatalogoMockConfig}).
 */
@Configuration
public class RestConfig {

    @Bean
    public RestClient catalogRestClient(@Value("${sales.catalog.url}") String catalogUrl) {
        return RestClient.builder()
                .baseUrl(catalogUrl)
                .build();
    }
}
