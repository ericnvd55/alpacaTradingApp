package com.trading.strategy.adapters.events;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.trading.core.domain.Bar;
import com.trading.core.domain.Order;
import com.trading.core.ports.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes to the order-events / market-data Pub/Sub topics (see
 * trading-infra/terraform/pubsub.tf). Active under gcp-gke profile.
 *
 * Ordering key is per client_order_id for order events and per symbol for
 * market data, matching the per-entity (not global) ordering guarantee
 * documented in docs/design.md — requires
 * spring.cloud.gcp.pubsub.publisher.enable-message-ordering=true.
 */
@Component
@Profile("gcp-gke")
public class GcpPubSubEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GcpPubSubEventPublisher.class);

    static final String ORDER_EVENTS_TOPIC = "order-events";
    static final String MARKET_DATA_TOPIC = "market-data";

    private final PubSubTemplate pubSubTemplate;

    public GcpPubSubEventPublisher(PubSubTemplate pubSubTemplate) {
        this.pubSubTemplate = pubSubTemplate;
    }

    @Override
    public void publishOrderFilled(Order order) {
        publishOrderEvent("order.filled", order);
    }

    @Override
    public void publishOrderCancelled(Order order) {
        publishOrderEvent("order.cancelled", order);
    }

    @Override
    public void publishMarketBar(Bar bar) {
        pubSubTemplate.publish(MARKET_DATA_TOPIC, bar, Map.of(
                "eventType", "market.bar",
                GcpPubSubHeaders.ORDERING_KEY, bar.symbol().ticker()
        )).exceptionally(ex -> {
            log.error("Failed to publish market.bar for {}", bar.symbol().ticker(), ex);
            return null;
        });
    }

    private void publishOrderEvent(String eventType, Order order) {
        pubSubTemplate.publish(ORDER_EVENTS_TOPIC, order, Map.of(
                "eventType", eventType,
                GcpPubSubHeaders.ORDERING_KEY, order.clientOrderId()
        )).exceptionally(ex -> {
            log.error("Failed to publish {} for order {}", eventType, order.clientOrderId(), ex);
            return null;
        });
    }
}
