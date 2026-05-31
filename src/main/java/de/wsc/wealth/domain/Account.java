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
    private String bank;

    @NotBlank
    private String accountNumber;

    private String description;

    private String iban;

    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    private AssetAllocation assetAllocation = AssetAllocation.RISIKOFREI;

    public Account() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getDisplayName() { return bank + " – " + accountNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public AssetAllocation getAssetAllocation() { return assetAllocation; }
    public void setAssetAllocation(AssetAllocation assetAllocation) { this.assetAllocation = assetAllocation; }
}
