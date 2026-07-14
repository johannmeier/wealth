package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ACCOUNT_CRITERIA_VALUE",
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "criteria_definition_id"}))
public class AccountCriteriaValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_definition_id", nullable = false)
    private CriteriaDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_option_id")
    private CriteriaOption option;

    private String freeTextValue;

    public AccountCriteriaValue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public CriteriaDefinition getDefinition() { return definition; }
    public void setDefinition(CriteriaDefinition definition) { this.definition = definition; }
    public CriteriaOption getOption() { return option; }
    public void setOption(CriteriaOption option) { this.option = option; }
    public String getFreeTextValue() { return freeTextValue; }
    public void setFreeTextValue(String freeTextValue) { this.freeTextValue = freeTextValue; }
}
