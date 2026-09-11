package com.trading.core.ports;

import com.trading.core.domain.Bar;
import com.trading.core.domain.Order;

/**
 * Port for publishing domain events to the order-events / market-data topics.
 * Adapters: GcpPubSubEventPublisher (gcp-gke profile), NoOpEventPublisher (default).
 */
public interface EventPublisher {
    void publishOrderFilled(Order order);
    void publishOrderCancelled(Order order);
    void publishMarketBar(Bar bar);
}
