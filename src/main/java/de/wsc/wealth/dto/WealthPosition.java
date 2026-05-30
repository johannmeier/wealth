package de.wsc.wealth.dto;

import de.wsc.wealth.domain.AssetAllocation;
import de.wsc.wealth.domain.AssetType;
import java.math.BigDecimal;

public class WealthPosition {
    private Long id;
    private String name;
    private String type; // "ASSET" or "ACCOUNT"
    private AssetType assetType;
    private AssetAllocation assetAllocation;
    private String indexName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal value;
    private BigDecimal percentage;
    private String currency;
    private String depotName;

    public WealthPosition() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public AssetType getAssetType() { return assetType; }
    public void setAssetType(AssetType assetType) { this.assetType = assetType; }
    public AssetAllocation getAssetAllocation() { return assetAllocation; }
    public void setAssetAllocation(AssetAllocation assetAllocation) { this.assetAllocation = assetAllocation; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDepotName() { return depotName; }
    public void setDepotName(String depotName) { this.depotName = depotName; }
}
