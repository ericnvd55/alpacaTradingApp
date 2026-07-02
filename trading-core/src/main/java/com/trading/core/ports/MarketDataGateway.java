package com.trading.core.ports;

import com.trading.core.domain.Bar;
import com.trading.core.domain.Symbol;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port for streaming market data. Adapter: AlpacaWebSocketGateway.
 */
public interface MarketDataGateway {
    void subscribe(List<Symbol> symbols, Consumer<Bar> onBar);
    void unsubscribe(List<Symbol> symbols);
}
