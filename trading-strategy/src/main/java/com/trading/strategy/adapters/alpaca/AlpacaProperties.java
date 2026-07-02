package com.trading.strategy.adapters.alpaca;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaProperties(
        String baseUrl,
        String wsUrl
) {
    public AlpacaProperties {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("alpaca.base-url must be set");
        if (wsUrl == null || wsUrl.isBlank())
            throw new IllegalArgumentException("alpaca.ws-url must be set");
    }
}
