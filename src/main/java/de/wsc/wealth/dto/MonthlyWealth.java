package de.wsc.wealth.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

public class MonthlyWealth {
    private final LocalDate month;
    private final BigDecimal assetsValue;
    private final BigDecimal accountsValue;
    private final BigDecimal coinsValue;

    public MonthlyWealth(LocalDate month, BigDecimal assetsValue, BigDecimal accountsValue, BigDecimal coinsValue) {
        this.month = month;
        this.assetsValue = assetsValue;
        this.accountsValue = accountsValue;
        this.coinsValue = coinsValue;
    }

    public LocalDate getMonth() { return month; }
    public BigDecimal getAssetsValue() { return assetsValue; }
    public BigDecimal getAccountsValue() { return accountsValue; }
    public BigDecimal getCoinsValue() { return coinsValue; }

    public BigDecimal getTotal() {
        return Stream.of(assetsValue, accountsValue, coinsValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
