package de.wsc.wealth.repository;

import de.wsc.wealth.domain.CriteriaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CriteriaDefinitionRepository extends JpaRepository<CriteriaDefinition, Long> {
    List<CriteriaDefinition> findAllByOrderBySortOrderAsc();
    Optional<CriteriaDefinition> findBySystemCode(String systemCode);
}
