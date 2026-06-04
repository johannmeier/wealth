package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankRepository extends JpaRepository<Bank, Long> {
    List<Bank> findAllByOrderByNameAsc();
}
