package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Entity
@Table(name = "depot")
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    /** Percentage (0-100) of the value that belongs to this app's user, e.g. 50 for a
        jointly owned depot split evenly. Null means fully owned (100%). */
    private BigDecimal ownershipShare;

    public Depot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public BigDecimal getOwnershipShare() { return ownershipShare; }
    public void setOwnershipShare(BigDecimal ownershipShare) { this.ownershipShare = ownershipShare; }

    /** {@link #ownershipShare} as a 0-1 multiplier, defaulting to 1 (full ownership) when unset. */
    public BigDecimal getOwnershipFactor() {
        return (ownershipShare != null ? ownershipShare : BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(100));
    }
}
