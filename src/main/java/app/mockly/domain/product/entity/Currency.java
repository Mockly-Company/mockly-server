package app.mockly.domain.product.entity;

import lombok.Getter;

@Getter
public enum Currency {
    KRW("원", "₩"),
    USD("달러", "$");

    private final String displayName;
    private final String symbol;

    Currency(String displayName, String symbol) {
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public io.portone.sdk.server.common.Currency toPortOneCurrency() {
        return switch (this) {
            case KRW -> io.portone.sdk.server.common.Currency.Krw.INSTANCE;
            case USD -> io.portone.sdk.server.common.Currency.Usd.INSTANCE;
        };
    }
}
