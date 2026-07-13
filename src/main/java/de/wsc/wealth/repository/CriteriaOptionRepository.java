package de.wsc.wealth.repository;

import de.wsc.wealth.domain.CriteriaDefinition;
import de.wsc.wealth.domain.CriteriaOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CriteriaOptionRepository extends JpaRepository<CriteriaOption, Long> {
    List<CriteriaOption> findByDefinitionOrderBySortOrderAsc(CriteriaDefinition definition);
    Optional<CriteriaOption> findByDefinitionAndSystemCode(CriteriaDefinition definition, String systemCode);
}
