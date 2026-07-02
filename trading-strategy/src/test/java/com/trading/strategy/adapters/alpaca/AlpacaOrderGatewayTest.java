package com.trading.strategy.adapters.alpaca;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.trading.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AlpacaOrderGatewayTest {

    private AlpacaOrderGateway gateway;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        RestClient client = RestClient.builder()
                .baseUrl(wm.getHttpBaseUrl())
                .build();
        gateway = new AlpacaOrderGateway(client);
    }

    @Test
    void submit_postsOrderAndReturnsNewOrder() {
        stubFor(post(urlEqualTo("/v2/orders"))
                .willReturn(okJson("""
                    {
                        "id": "broker-id-123",
                        "client_order_id": "client-abc",
                        "symbol": "AAPL",
                        "side": "buy",
                        "type": "market",
                        "qty": "10",
                        "limit_price": null,
                        "status": "new",
                        "created_at": "2024-01-15T10:00:00Z",
                        "updated_at": "2024-01-15T10:00:00Z"
                    }
                """)));

        Order order = gateway.submit("client-abc", new Symbol("AAPL"),
                OrderSide.BUY, OrderType.MARKET, BigDecimal.TEN, null);

        assertThat(order.clientOrderId()).isEqualTo("client-abc");
        assertThat(order.brokerOrderId()).isEqualTo("broker-id-123");
        assertThat(order.symbol().ticker()).isEqualTo("AAPL");
        assertThat(order.side()).isEqualTo(OrderSide.BUY);
        assertThat(order.type()).isEqualTo(OrderType.MARKET);
        assertThat(order.quantity()).isEqualByComparingTo("10");
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void submit_includesLimitPriceWhenProvided() {
        stubFor(post(urlEqualTo("/v2/orders"))
                .withRequestBody(matchingJsonPath("$.limit_price", equalTo("150.50")))
                .willReturn(okJson("""
                    {
                        "id": "broker-id-456",
                        "client_order_id": "client-def",
                        "symbol": "AAPL",
                        "side": "buy",
                        "type": "limit",
                        "qty": "5",
                        "limit_price": "150.50",
                        "status": "new",
                        "created_at": "2024-01-15T10:00:00Z",
                        "updated_at": "2024-01-15T10:00:00Z"
                    }
                """)));

        Order order = gateway.submit("client-def", new Symbol("AAPL"),
                OrderSide.BUY, OrderType.LIMIT, new BigDecimal("5"), new BigDecimal("150.50"));

        assertThat(order.type()).isEqualTo(OrderType.LIMIT);
        assertThat(order.limitPrice()).isEqualByComparingTo("150.50");
    }

    @Test
    void getOrder_fetchesByClientOrderId() {
        stubFor(get(urlEqualTo("/v2/orders:by_client_order_id?client_order_id=client-abc"))
                .willReturn(okJson("""
                    {
                        "id": "broker-id-123",
                        "client_order_id": "client-abc",
                        "symbol": "AAPL",
                        "side": "buy",
                        "type": "market",
                        "qty": "10",
                        "limit_price": null,
                        "status": "filled",
                        "created_at": "2024-01-15T10:00:00Z",
                        "updated_at": "2024-01-15T10:01:00Z"
                    }
                """)));

        Order order = gateway.getOrder("client-abc");

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.brokerOrderId()).isEqualTo("broker-id-123");
    }

    @Test
    void cancel_deletesOrderByBrokerIdAndReturnsFinalState() {
        stubFor(get(urlEqualTo("/v2/orders:by_client_order_id?client_order_id=client-abc"))
                .willReturn(okJson("""
                    {
                        "id": "broker-id-123",
                        "client_order_id": "client-abc",
                        "symbol": "AAPL",
                        "side": "buy",
                        "type": "market",
                        "qty": "10",
                        "limit_price": null,
                        "status": "canceled",
                        "created_at": "2024-01-15T10:00:00Z",
                        "updated_at": "2024-01-15T10:00:01Z"
                    }
                """)));

        stubFor(delete(urlEqualTo("/v2/orders/broker-id-123"))
                .willReturn(noContent()));

        Order order = gateway.cancel("client-abc");

        verify(deleteRequestedFor(urlEqualTo("/v2/orders/broker-id-123")));
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELED);
    }
}
