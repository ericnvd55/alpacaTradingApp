package com.trading.core.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String clientOrderId,
        String brokerOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal limitPrice,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
