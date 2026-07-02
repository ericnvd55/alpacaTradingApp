package com.trading.core.ports;

import com.trading.core.domain.Order;
import com.trading.core.domain.OrderSide;
import com.trading.core.domain.OrderType;
import com.trading.core.domain.Symbol;

import java.math.BigDecimal;

/**
 * Port for order execution. Adapter: AlpacaOrderGateway.
 */
public interface OrderGateway {
    Order submit(String clientOrderId, Symbol symbol, OrderSide side, OrderType type,
                 BigDecimal quantity, BigDecimal limitPrice);
    Order getOrder(String clientOrderId);
    Order cancel(String clientOrderId);
}
