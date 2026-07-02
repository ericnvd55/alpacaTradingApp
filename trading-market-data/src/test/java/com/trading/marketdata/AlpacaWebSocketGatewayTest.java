package com.trading.marketdata;

import com.trading.core.domain.Bar;
import com.trading.core.domain.Symbol;
import com.trading.core.ports.SecretStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class AlpacaWebSocketGatewayTest {

    @Mock
    WebSocketSession session;

    AlpacaWebSocketGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        AlpacaMarketDataProperties props =
                new AlpacaMarketDataProperties("wss://stream.data.alpaca.markets/v2/iex");
        SecretStore secrets = key -> key.contains("KEY") ? "test-key" : "test-secret";

        gateway = new AlpacaWebSocketGateway(props, secrets);

        doNothing().when(session).sendMessage(any());
        gateway.afterConnectionEstablished(session);
    }

    @Test
    void dispatchesBarToSubscribedHandler() throws Exception {
        List<Bar> received = new ArrayList<>();
        gateway.subscribe(List.of(new Symbol("AAPL")), received::add);

        gateway.handleTextMessage(session, new TextMessage("""
                [{"T":"b","S":"AAPL","t":"2024-01-15T10:00:00Z",
                  "o":150.00,"h":151.00,"l":149.50,"c":150.75,
                  "v":5000,"vw":150.40,"n":120}]
                """));

        assertThat(received).hasSize(1);
        Bar bar = received.get(0);
        assertThat(bar.symbol().ticker()).isEqualTo("AAPL");
        assertThat(bar.open()).isEqualByComparingTo("150.00");
        assertThat(bar.high()).isEqualByComparingTo("151.00");
        assertThat(bar.low()).isEqualByComparingTo("149.50");
        assertThat(bar.close()).isEqualByComparingTo("150.75");
        assertThat(bar.volume()).isEqualTo(5000L);
        assertThat(bar.tradeCount()).isEqualTo(120);
    }

    @Test
    void dispatchesBarToMultipleHandlers() throws Exception {
        List<Bar> firstHandler = new ArrayList<>();
        List<Bar> secondHandler = new ArrayList<>();
        gateway.subscribe(List.of(new Symbol("AAPL")), firstHandler::add);
        gateway.subscribe(List.of(new Symbol("AAPL")), secondHandler::add);

        gateway.handleTextMessage(session, new TextMessage("""
                [{"T":"b","S":"AAPL","t":"2024-01-15T10:00:00Z",
                  "o":150.00,"h":151.00,"l":149.50,"c":150.75,
                  "v":5000,"vw":150.40,"n":120}]
                """));

        assertThat(firstHandler).hasSize(1);
        assertThat(secondHandler).hasSize(1);
    }

    @Test
    void doesNotDispatchOnAuthSuccessMessage() throws Exception {
        List<Bar> received = new ArrayList<>();
        gateway.subscribe(List.of(new Symbol("AAPL")), received::add);

        gateway.handleTextMessage(session, new TextMessage("""
                [{"T":"success","msg":"authenticated"}]
                """));

        assertThat(received).isEmpty();
    }

    @Test
    void doesNotDispatchOnSubscriptionConfirmation() throws Exception {
        List<Bar> received = new ArrayList<>();
        gateway.subscribe(List.of(new Symbol("AAPL")), received::add);

        gateway.handleTextMessage(session, new TextMessage("""
                [{"T":"subscription","bars":["AAPL"]}]
                """));

        assertThat(received).isEmpty();
    }
}
