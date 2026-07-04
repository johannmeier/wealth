package de.wsc.wealth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ibkr_config")
public class IbkrConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String queryId;

    @Column(nullable = false, length = 2048)
    private String token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    public IbkrConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQueryId() { return queryId; }
    public void setQueryId(String queryId) { this.queryId = queryId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
}
