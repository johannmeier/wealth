package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "bullionvault_config")
public class BullionVaultConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gold_asset_id")
    private Asset goldAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "silver_asset_id")
    private Asset silverAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platinum_asset_id")
    private Asset platinumAsset;

    public BullionVaultConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public Asset getGoldAsset() { return goldAsset; }
    public void setGoldAsset(Asset goldAsset) { this.goldAsset = goldAsset; }
    public Asset getSilverAsset() { return silverAsset; }
    public void setSilverAsset(Asset silverAsset) { this.silverAsset = silverAsset; }
    public Asset getPlatinumAsset() { return platinumAsset; }
    public void setPlatinumAsset(Asset platinumAsset) { this.platinumAsset = platinumAsset; }
}
