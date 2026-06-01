package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ASSET")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String isin;
    private String symbol;

    @Enumerated(EnumType.STRING)
    private AssetCategory category;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(255)")
    private AssetType type;

    @Enumerated(EnumType.STRING)
    private AssetAllocation assetAllocation;

    private String indexName;
    private String currency = "EUR";
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean archived = false;

    @Column(precision = 19, scale = 10)
    private BigDecimal currentPrice;
    private LocalDateTime lastPriceUpdate;

    public Asset() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public AssetCategory getCategory() { return category; }
    public void setCategory(AssetCategory category) { this.category = category; }
    public AssetType getType() { return type; }
    public void setType(AssetType type) { this.type = type; }
    public AssetAllocation getAssetAllocation() { return assetAllocation; }
    public void setAssetAllocation(AssetAllocation assetAllocation) { this.assetAllocation = assetAllocation; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public LocalDateTime getLastPriceUpdate() { return lastPriceUpdate; }
    public void setLastPriceUpdate(LocalDateTime lastPriceUpdate) { this.lastPriceUpdate = lastPriceUpdate; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public boolean isAutoPrice() {
        return category == AssetCategory.BOERSENGEHANDELT
            || category == AssetCategory.EDELMETALL
            || type == AssetType.KRYPTO;
    }
}
