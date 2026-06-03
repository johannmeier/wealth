package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Long> {
    List<AccountBalance> findByAccountOrderByDateDesc(Account account);
    Optional<AccountBalance> findFirstByAccountOrderByDateDesc(Account account);

    @Query("SELECT b FROM AccountBalance b JOIN FETCH b.account")
    List<AccountBalance> findAllWithAccount();
}
