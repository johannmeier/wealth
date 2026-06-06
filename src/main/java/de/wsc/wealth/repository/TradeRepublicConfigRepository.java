package de.wsc.wealth.repository;

import de.wsc.wealth.domain.TradeRepublicConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TradeRepublicConfigRepository extends JpaRepository<TradeRepublicConfig, Long> {
    Optional<TradeRepublicConfig> findByPhoneNumber(String phoneNumber);
}
