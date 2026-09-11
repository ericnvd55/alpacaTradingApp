package com.trading.strategy.adapters.events;

import com.trading.core.domain.Bar;
import com.trading.core.domain.Order;
import com.trading.core.ports.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Discards events. Active when NOT on gcp-gke profile, so local/dev runs
 * don't require a live Pub/Sub project.
 */
@Component
@Profile("!gcp-gke")
public class NoOpEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventPublisher.class);

    @Override
    public void publishOrderFilled(Order order) {
        log.debug("NoOpEventPublisher: order.filled {} (not published)", order.clientOrderId());
    }

    @Override
    public void publishOrderCancelled(Order order) {
        log.debug("NoOpEventPublisher: order.cancelled {} (not published)", order.clientOrderId());
    }

    @Override
    public void publishMarketBar(Bar bar) {
        log.debug("NoOpEventPublisher: market.bar {} (not published)", bar.symbol().ticker());
    }
}
