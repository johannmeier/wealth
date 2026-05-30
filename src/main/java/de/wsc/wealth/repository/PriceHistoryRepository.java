package de.wsc.wealth.repository;

import de.wsc.wealth.domain.PriceHistory;
import de.wsc.wealth.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByAssetOrderByDateAsc(Asset asset);
}
