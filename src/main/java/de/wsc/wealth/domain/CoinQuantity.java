package de.wsc.wealth.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "coin_quantity")
public class CoinQuantity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coin_id", nullable = false)
    private Coin coin;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer quantity;

    public CoinQuantity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Coin getCoin() { return coin; }
    public void setCoin(Coin coin) { this.coin = coin; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
