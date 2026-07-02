package com.trading.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trading.core.domain.Bar;
import com.trading.core.domain.Symbol;
import com.trading.core.ports.MarketDataGateway;
import com.trading.core.ports.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class AlpacaWebSocketGateway extends TextWebSocketHandler implements MarketDataGateway {

    private static final Logger log = LoggerFactory.getLogger(AlpacaWebSocketGateway.class);

    private final String wsUrl;
    private final String apiKey;
    private final String apiSecret;
    private final ObjectMapper mapper;

    private WebSocketSession session;
    private final CountDownLatch authLatch = new CountDownLatch(1);
    private final List<Consumer<Bar>> barHandlers = new CopyOnWriteArrayList<>();

    public AlpacaWebSocketGateway(AlpacaMarketDataProperties props, SecretStore secrets) {
        this.wsUrl = props.wsUrl();
        this.apiKey = secrets.get("ALPACA_API_KEY");
        this.apiSecret = secrets.get("ALPACA_API_SECRET");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void connect() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        session = client.execute(this, wsUrl).get(10, TimeUnit.SECONDS);
        if (!authLatch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Alpaca WebSocket authentication timed out");
        }
    }

    @Override
    public void subscribe(List<Symbol> symbols, Consumer<Bar> onBar) {
        barHandlers.add(onBar);
        List<String> tickers = symbols.stream().map(Symbol::ticker).toList();
        sendJson(Map.of("action", "subscribe", "bars", tickers));
    }

    @Override
    public void unsubscribe(List<Symbol> symbols) {
        List<String> tickers = symbols.stream().map(Symbol::ticker).toList();
        sendJson(Map.of("action", "unsubscribe", "bars", tickers));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        log.info("Alpaca WebSocket connected — authenticating");
        sendJson(Map.of("action", "auth", "key", apiKey, "secret", apiSecret));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        List<AlpacaMessage> messages = mapper.readValue(
                message.getPayload(), new TypeReference<>() {});

        for (AlpacaMessage msg : messages) {
            switch (msg.type()) {
                case "success" -> {
                    if ("authenticated".equals(msg.msg())) {
                        log.info("Alpaca WebSocket authenticated");
                        authLatch.countDown();
                    }
                }
                case "b" -> dispatchBar(msg);
                case "error" -> log.error("Alpaca error: code={} msg={}", msg.code(), msg.msg());
                default -> log.debug("Alpaca message type={}", msg.type());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.error("Alpaca WebSocket transport error", ex);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("Alpaca WebSocket closed: {}", status);
    }

    private void dispatchBar(AlpacaMessage msg) {
        Bar bar = new Bar(
                new Symbol(msg.symbol()),
                msg.timestamp(),
                msg.open(),
                msg.high(),
                msg.low(),
                msg.close(),
                msg.volume(),
                msg.vwap(),
                msg.tradeCount()
        );
        barHandlers.forEach(h -> h.accept(bar));
    }

    private void sendJson(Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send WebSocket message", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AlpacaMessage(
            @JsonProperty("T") String type,
            @JsonProperty("msg") String msg,
            @JsonProperty("code") Integer code,
            @JsonProperty("S") String symbol,
            @JsonProperty("t") Instant timestamp,
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("c") BigDecimal close,
            @JsonProperty("v") long volume,
            @JsonProperty("vw") BigDecimal vwap,
            @JsonProperty("n") int tradeCount
    ) {}
}
