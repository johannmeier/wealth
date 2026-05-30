package de.wsc.wealth.dto;

import java.math.BigDecimal;
import java.util.List;

public class StatisticsGroup {
    private String name;
    private List<WealthPosition> positions;
    private BigDecimal totalValue;
    private BigDecimal percentage;

    public StatisticsGroup() {}

    public StatisticsGroup(String name, List<WealthPosition> positions, BigDecimal totalValue, BigDecimal percentage) {
        this.name = name;
        this.positions = positions;
        this.totalValue = totalValue;
        this.percentage = percentage;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<WealthPosition> getPositions() { return positions; }
    public void setPositions(List<WealthPosition> positions) { this.positions = positions; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
}
