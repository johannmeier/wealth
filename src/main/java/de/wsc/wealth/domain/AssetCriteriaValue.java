package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ASSET_CRITERIA_VALUE",
       uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "criteria_definition_id"}))
public class AssetCriteriaValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_definition_id", nullable = false)
    private CriteriaDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_option_id")
    private CriteriaOption option;

    private String freeTextValue;

    public AssetCriteriaValue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public CriteriaDefinition getDefinition() { return definition; }
    public void setDefinition(CriteriaDefinition definition) { this.definition = definition; }
    public CriteriaOption getOption() { return option; }
    public void setOption(CriteriaOption option) { this.option = option; }
    public String getFreeTextValue() { return freeTextValue; }
    public void setFreeTextValue(String freeTextValue) { this.freeTextValue = freeTextValue; }
}
