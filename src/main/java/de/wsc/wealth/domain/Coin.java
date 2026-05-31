package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Entity
@Table(name = "COIN")
public class Coin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    private CoinMetal metal;

    @Column(precision = 19, scale = 10)
    private BigDecimal weightGrams;

    private Integer mintYear;

    @Column(precision = 19, scale = 10)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    public Coin() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CoinMetal getMetal() { return metal; }
    public void setMetal(CoinMetal metal) { this.metal = metal; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal weightGrams) { this.weightGrams = weightGrams; }
    public Integer getMintYear() { return mintYear; }
    public void setMintYear(Integer mintYear) { this.mintYear = mintYear; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Depot getDepot() { return depot; }
    public void setDepot(Depot depot) { this.depot = depot; }

    public BigDecimal getWeightOz() {
        if (weightGrams == null) return null;
        return weightGrams.divide(new BigDecimal("31.1035"), 10, java.math.RoundingMode.HALF_UP);
    }
}
