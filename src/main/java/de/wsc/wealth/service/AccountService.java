package de.wsc.wealth.service;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AccountBalance;
import de.wsc.wealth.repository.AccountBalanceRepository;
import de.wsc.wealth.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;

    public AccountService(AccountRepository accountRepository, AccountBalanceRepository balanceRepository) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() { return accountRepository.findAllByOrderByBankNameAscAccountNumberAsc(); }

    public List<Account> findByBankId(Long bankId) { return accountRepository.findByBankIdOrderByAccountNumberAsc(bankId); }

    public List<Account> findWithoutBank() { return accountRepository.findByBankIsNullOrderByAccountNumberAsc(); }

    @Transactional(readOnly = true)
    public Optional<Account> findById(Long id) { return accountRepository.findById(id); }

    public Account save(Account account) { return accountRepository.save(account); }

    public void delete(Long id) { accountRepository.deleteById(id); }

    public AccountBalance saveBalance(Long accountId, AccountBalance balance) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        balance.setId(null);
        balance.setAccount(account);
        return balanceRepository.save(balance);
    }

    @Transactional(readOnly = true)
    public List<AccountBalance> getBalances(Long accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        return balanceRepository.findByAccountOrderByDateDesc(account);
    }

    public void deleteBalance(Long balanceId) {
        balanceRepository.deleteById(balanceId);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getLatestBalancesByAccountId() {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (AccountBalance ab : balanceRepository.findLatestPerAccount()) {
            result.put(ab.getAccount().getId(), ab.getBalance());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, LocalDate> getLatestBalanceDatesByAccountId() {
        Map<Long, LocalDate> result = new LinkedHashMap<>();
        for (AccountBalance ab : balanceRepository.findLatestPerAccount()) {
            result.put(ab.getAccount().getId(), ab.getDate());
        }
        return result;
    }
}
