package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByOrderByBankNameAscAccountNumberAsc();
    List<Account> findByBankIdOrderByAccountNumberAsc(Long bankId);
    List<Account> findByBankIsNullOrderByAccountNumberAsc();
}
