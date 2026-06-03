package de.wsc.wealth.repository;

import de.wsc.wealth.domain.PriceHistory;
import de.wsc.wealth.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByAssetOrderByDateAsc(Asset asset);
    void deleteByAsset(Asset asset);

    @Query("SELECT p FROM PriceHistory p JOIN FETCH p.asset")
    List<PriceHistory> findAllWithAsset();
}
