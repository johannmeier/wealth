package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Non-sensitive FinTS connection settings for one bank.
 * The user ID is persisted for convenience (pre-fills the sync form); the PIN is never
 * persisted anywhere — it is entered by the user on every sync.
 */
@Entity
@Table(name = "FINTS_CONFIG")
public class FintsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String blz;

    @NotBlank
    @Column(nullable = false, length = 500)
    private String fintsUrl;

    private String tanVerfahren;

    private String userId;

    /** Percentage (0-100) of this bank's accounts/depots that belongs to this app's user.
        Applied to all Account/Depot records linked to {@link #bank} whenever this is set
        (see FintsService#saveConfig). Null means fully owned (100%). */
    private BigDecimal ownershipShare;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    public FintsConfig() {}

    public Long getId() { return id; }
    public String getBlz() { return blz; }
    public void setBlz(String blz) { this.blz = blz; }
    public String getFintsUrl() { return fintsUrl; }
    public void setFintsUrl(String fintsUrl) { this.fintsUrl = fintsUrl; }
    public String getTanVerfahren() { return tanVerfahren; }
    public void setTanVerfahren(String tanVerfahren) { this.tanVerfahren = tanVerfahren; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getOwnershipShare() { return ownershipShare; }
    public void setOwnershipShare(BigDecimal ownershipShare) { this.ownershipShare = ownershipShare; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
}
