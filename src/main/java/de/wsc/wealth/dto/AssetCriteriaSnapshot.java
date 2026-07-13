package de.wsc.wealth.dto;

public class AssetCriteriaSnapshot {
    private String categoryLabel;
    private String categoryCode;
    private String typeLabel;
    private String typeCode;
    private String allocationLabel;
    private String allocationCode;
    private String distributionLabel;
    private String distributionCode;
    private String indexName;

    public String getCategoryLabel() { return categoryLabel; }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getAllocationLabel() { return allocationLabel; }
    public void setAllocationLabel(String allocationLabel) { this.allocationLabel = allocationLabel; }
    public String getAllocationCode() { return allocationCode; }
    public void setAllocationCode(String allocationCode) { this.allocationCode = allocationCode; }
    public String getDistributionLabel() { return distributionLabel; }
    public void setDistributionLabel(String distributionLabel) { this.distributionLabel = distributionLabel; }
    public String getDistributionCode() { return distributionCode; }
    public void setDistributionCode(String distributionCode) { this.distributionCode = distributionCode; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public boolean isAutoPrice() {
        return "BOERSENGEHANDELT".equals(categoryCode)
            || "EDELMETALL".equals(categoryCode)
            || "KRYPTO".equals(typeCode);
    }
}
