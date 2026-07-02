package com.trading.core.domain;

public record Symbol(String ticker) {
    public Symbol {
        if (ticker == null || ticker.isBlank()) throw new IllegalArgumentException("ticker must not be blank");
        ticker = ticker.toUpperCase();
    }

    @Override
    public String toString() {
        return ticker;
    }
}
