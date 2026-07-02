package com.trading.marketdata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlpacaMarketDataProperties.class)
public class MarketDataConfig {}
