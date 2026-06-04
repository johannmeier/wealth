package de.wsc.wealth.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "account_balance")
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 10)
    private BigDecimal balance;

    public AccountBalance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
