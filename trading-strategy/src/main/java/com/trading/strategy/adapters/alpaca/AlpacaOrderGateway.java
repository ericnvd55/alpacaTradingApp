package com.trading.strategy.adapters.alpaca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trading.core.domain.*;
import com.trading.core.ports.OrderGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class AlpacaOrderGateway implements OrderGateway {

    private final RestClient restClient;

    public AlpacaOrderGateway(RestClient alpacaRestClient) {
        this.restClient = alpacaRestClient;
    }

    @Override
    public Order submit(String clientOrderId, Symbol symbol, OrderSide side,
                        OrderType type, BigDecimal quantity, BigDecimal limitPrice) {
        var request = new OrderRequest(
                symbol.ticker(),
                quantity.toPlainString(),
                side.name().toLowerCase(),
                type.name().toLowerCase(),
                "day",
                limitPrice != null ? limitPrice.toPlainString() : null,
                clientOrderId
        );

        AlpacaOrderResponse resp = restClient.post()
                .uri("/v2/orders")
                .body(request)
                .retrieve()
                .body(AlpacaOrderResponse.class);

        return toOrder(resp);
    }

    @Override
    public Order getOrder(String clientOrderId) {
        AlpacaOrderResponse resp = restClient.get()
                .uri("/v2/orders:by_client_order_id?client_order_id={id}", clientOrderId)
                .retrieve()
                .body(AlpacaOrderResponse.class);
        return toOrder(resp);
    }

    @Override
    public Order cancel(String clientOrderId) {
        Order existing = getOrder(clientOrderId);
        restClient.delete()
                .uri("/v2/orders/{id}", existing.brokerOrderId())
                .retrieve()
                .toBodilessEntity();
        return getOrder(clientOrderId);
    }

    private Order toOrder(AlpacaOrderResponse r) {
        return new Order(
                r.clientOrderId(),
                r.id(),
                new Symbol(r.symbol()),
                OrderSide.valueOf(r.side().toUpperCase()),
                OrderType.valueOf(r.orderType().toUpperCase()),
                new BigDecimal(r.qty()),
                r.limitPrice() != null ? new BigDecimal(r.limitPrice()) : null,
                mapStatus(r.status()),
                r.createdAt(),
                r.updatedAt()
        );
    }

    private OrderStatus mapStatus(String raw) {
        return switch (raw) {
            case "new" -> OrderStatus.NEW;
            case "partially_filled" -> OrderStatus.PARTIALLY_FILLED;
            case "filled" -> OrderStatus.FILLED;
            case "canceled" -> OrderStatus.CANCELED;
            case "expired" -> OrderStatus.EXPIRED;
            case "rejected" -> OrderStatus.REJECTED;
            case "pending_cancel" -> OrderStatus.PENDING_CANCEL;
            default -> OrderStatus.NEW;
        };
    }

    record OrderRequest(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("qty") String qty,
            @JsonProperty("side") String side,
            @JsonProperty("type") String type,
            @JsonProperty("time_in_force") String timeInForce,
            @JsonProperty("limit_price") String limitPrice,
            @JsonProperty("client_order_id") String clientOrderId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AlpacaOrderResponse(
            @JsonProperty("id") String id,
            @JsonProperty("client_order_id") String clientOrderId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("side") String side,
            @JsonProperty("type") String orderType,
            @JsonProperty("qty") String qty,
            @JsonProperty("limit_price") String limitPrice,
            @JsonProperty("status") String status,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}
}
