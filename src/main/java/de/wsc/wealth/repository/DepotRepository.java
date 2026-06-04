package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Depot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepotRepository extends JpaRepository<Depot, Long> {
    List<Depot> findAllByOrderByNameAsc();
    List<Depot> findByBankIdOrderByNameAsc(Long bankId);
    List<Depot> findByBankIsNullOrderByNameAsc();
}
