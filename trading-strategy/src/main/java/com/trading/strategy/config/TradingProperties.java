package com.trading.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(List<String> symbols) {
    public TradingProperties {
        if (symbols == null || symbols.isEmpty())
            throw new IllegalArgumentException("trading.symbols must be set");
    }
}
