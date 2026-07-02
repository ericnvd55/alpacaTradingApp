package com.trading.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Bar(
        Symbol symbol,
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal vwap,
        int tradeCount
) {}
