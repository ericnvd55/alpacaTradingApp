package com.trading.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaMarketDataProperties(String wsUrl) {
    public AlpacaMarketDataProperties {
        if (wsUrl == null || wsUrl.isBlank())
            throw new IllegalArgumentException("alpaca.ws-url must be set");
    }
}
