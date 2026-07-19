package de.wsc.wealth.repository;

import de.wsc.wealth.domain.IbkrConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IbkrConfigRepository extends JpaRepository<IbkrConfig, Long> {
    Optional<IbkrConfig> findByQueryId(String queryId);
}
