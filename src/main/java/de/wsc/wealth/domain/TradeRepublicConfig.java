package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "TRADE_REPUBLIC_CONFIG")
public class TradeRepublicConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(length = 2048)
    private String wafToken;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    public TradeRepublicConfig() {}

    public Long getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getWafToken() { return wafToken; }
    public void setWafToken(String wafToken) { this.wafToken = wafToken; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
}
