package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "CRITERIA_DEFINITION")
public class CriteriaDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CriteriaValueType valueType;

    @Column(unique = true)
    private String systemCode;

    @Column(nullable = false)
    private int sortOrder;

    public CriteriaDefinition() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CriteriaValueType getValueType() { return valueType; }
    public void setValueType(CriteriaValueType valueType) { this.valueType = valueType; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isDeletable() {
        return systemCode == null;
    }
}
