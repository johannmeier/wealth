package de.wsc.wealth.repository;

import de.wsc.wealth.domain.BullionVaultConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BullionVaultConfigRepository extends JpaRepository<BullionVaultConfig, Long> {
    Optional<BullionVaultConfig> findByUsername(String username);
}
