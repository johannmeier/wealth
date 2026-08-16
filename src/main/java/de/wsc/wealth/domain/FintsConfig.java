package de.wsc.wealth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Non-sensitive FinTS connection settings for one bank.
 * User ID and PIN are never persisted here — they are entered by the user on every sync.
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
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
}
