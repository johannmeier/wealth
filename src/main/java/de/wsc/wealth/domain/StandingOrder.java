package de.wsc.wealth.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "standing_order")
public class StandingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_interval")
    private OrderInterval interval;

    private LocalDate startDate;
    private LocalDate nextDate;
    private boolean active = true;

    public StandingOrder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public Depot getDepot() { return depot; }
    public void setDepot(Depot depot) { this.depot = depot; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public OrderInterval getInterval() { return interval; }
    public void setInterval(OrderInterval interval) { this.interval = interval; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
