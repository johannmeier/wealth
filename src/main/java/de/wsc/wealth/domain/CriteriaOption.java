package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "CRITERIA_OPTION")
public class CriteriaOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_definition_id", nullable = false)
    private CriteriaDefinition definition;

    @NotBlank
    @Column(name = "value_text")
    private String value;

    private String systemCode;

    @Column(nullable = false)
    private int sortOrder;

    public CriteriaOption() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CriteriaDefinition getDefinition() { return definition; }
    public void setDefinition(CriteriaDefinition definition) { this.definition = definition; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isDeletable() {
        return systemCode == null;
    }
}
