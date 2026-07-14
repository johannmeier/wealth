package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Account;
import de.wsc.wealth.domain.AccountCriteriaValue;
import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AccountCriteriaValueRepository extends JpaRepository<AccountCriteriaValue, Long> {
    Optional<AccountCriteriaValue> findByAccountAndDefinition(Account account, CriteriaDefinition definition);
    List<AccountCriteriaValue> findByAccount(Account account);
    void deleteByDefinition(CriteriaDefinition definition);
    void deleteByOption(CriteriaOption option);

    @Query("SELECT v FROM AccountCriteriaValue v JOIN FETCH v.account JOIN FETCH v.definition LEFT JOIN FETCH v.option")
    List<AccountCriteriaValue> findAllWithAccountAndDefinitionAndOption();
}
