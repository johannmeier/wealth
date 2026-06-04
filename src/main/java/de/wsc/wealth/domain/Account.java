package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    private String accountNumber;

    private String description;

    private String iban;

    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    private AssetAllocation assetAllocation = AssetAllocation.RISIKOFREI;

    public Account() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getDisplayName() {
        String bankName = bank != null ? bank.getName() : "";
        String acct = accountNumber != null && !accountNumber.isBlank() ? accountNumber : "";
        if (!bankName.isBlank() && !acct.isBlank()) return bankName + " – " + acct;
        if (!bankName.isBlank()) return bankName;
        if (!acct.isBlank()) return acct;
        if (iban != null && !iban.isBlank()) return iban;
        if (description != null && !description.isBlank()) return description;
        return "Konto " + id;
    }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public AssetAllocation getAssetAllocation() { return assetAllocation; }
    public void setAssetAllocation(AssetAllocation assetAllocation) { this.assetAllocation = assetAllocation; }
}
