package com.trading.strategy.adapters.events;

import com.trading.core.domain.Symbol;
import com.trading.core.ports.EventPublisher;
import com.trading.marketdata.AlpacaWebSocketGateway;
import com.trading.strategy.config.TradingProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges the Alpaca market-data WebSocket to the market-data Pub/Sub topic —
 * the "market data gateway" responsibility from docs/design.md's target
 * architecture, forwarding every bar it receives independent of whatever the
 * (not yet written) strategy engine separately does with it in-process.
 * Active under gcp-gke profile; depends on the concrete AlpacaWebSocketGateway
 * rather than just the MarketDataGateway port because establishing the
 * connection is adapter-specific setup, not a port-level operation.
 */
@Component
@Profile("gcp-gke")
public class MarketDataEventForwarder {

    private static final Logger log = LoggerFactory.getLogger(MarketDataEventForwarder.class);

    private final AlpacaWebSocketGateway gateway;
    private final EventPublisher eventPublisher;
    private final TradingProperties tradingProperties;

    public MarketDataEventForwarder(AlpacaWebSocketGateway gateway, EventPublisher eventPublisher,
                                     TradingProperties tradingProperties) {
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
        this.tradingProperties = tradingProperties;
    }

    @PostConstruct
    void start() throws Exception {
        List<Symbol> symbols = tradingProperties.symbols().stream().map(Symbol::new).toList();
        gateway.connect();
        gateway.subscribe(symbols, eventPublisher::publishMarketBar);
        log.info("Forwarding market data bars to Pub/Sub for symbols: {}", tradingProperties.symbols());
    }
}
