package de.wsc.wealth.repository;

import de.wsc.wealth.domain.FintsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FintsConfigRepository extends JpaRepository<FintsConfig, Long> {
    Optional<FintsConfig> findByBlz(String blz);
}
