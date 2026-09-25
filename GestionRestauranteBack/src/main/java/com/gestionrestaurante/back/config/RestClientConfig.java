package com.gestionrestaurante.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * El proyecto usa {@code spring-boot-starter-webmvc} (no el starter general
 * {@code spring-boot-starter-web}), asi que la autoconfiguracion de Spring Boot que registraria
 * un bean de {@code RestClient.Builder} no esta en el classpath. Se declara aqui a mano para que
 * {@link com.gestionrestaurante.back.service.google.GoogleBusinessProfileClientImpl} pueda
 * recibirlo por constructor (necesario para poder atar un {@code MockRestServiceServer} en
 * tests - ver {@code GoogleBusinessProfileClientImplTest}).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
