package com.trading.strategy.adapters.alpaca;

import com.trading.core.ports.SecretStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AlpacaProperties.class)
public class AlpacaConfig {

    static final String KEY_HEADER = "APCA-API-KEY-ID";
    static final String SECRET_HEADER = "APCA-API-SECRET-KEY";

    @Bean
    RestClient alpacaRestClient(AlpacaProperties props, SecretStore secrets) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(KEY_HEADER, secrets.get("ALPACA_API_KEY"))
                .defaultHeader(SECRET_HEADER, secrets.get("ALPACA_API_SECRET"))
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
