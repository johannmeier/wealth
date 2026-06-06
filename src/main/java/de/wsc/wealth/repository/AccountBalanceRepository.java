package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {
    List<AccountBalance> findByAccountOrderByDateDesc(Account account);
    Optional<AccountBalance> findFirstByAccountOrderByDateDesc(Account account);
    Optional<AccountBalance> findByAccountAndDate(Account account, LocalDate date);

    @Query("SELECT b FROM AccountBalance b JOIN FETCH b.account")
    List<AccountBalance> findAllWithAccount();

    @Query("SELECT b FROM AccountBalance b JOIN FETCH b.account WHERE b.date = (SELECT MAX(b2.date) FROM AccountBalance b2 WHERE b2.account.id = b.account.id)")
    List<AccountBalance> findLatestPerAccount();
}
