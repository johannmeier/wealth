package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    private AssetAllocation assetAllocation = AssetAllocation.RISIKOFREI;

    public Account() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public AssetAllocation getAssetAllocation() { return assetAllocation; }
    public void setAssetAllocation(AssetAllocation assetAllocation) { this.assetAllocation = assetAllocation; }
}
