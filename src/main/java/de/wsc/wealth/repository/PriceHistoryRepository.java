package de.wsc.wealth.repository;

import de.wsc.wealth.domain.PriceHistory;
import de.wsc.wealth.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByAssetOrderByDateAsc(Asset asset);
    void deleteByAsset(Asset asset);
    boolean existsByAssetAndDate(Asset asset, LocalDate date);
    Optional<PriceHistory> findByAssetAndDate(Asset asset, LocalDate date);
    Optional<PriceHistory> findFirstByAssetOrderByDateDesc(Asset asset);

    @Query("SELECT p FROM PriceHistory p JOIN FETCH p.asset")
    List<PriceHistory> findAllWithAsset();

    @Query("SELECT p FROM PriceHistory p JOIN FETCH p.asset WHERE p.date = (SELECT MAX(p2.date) FROM PriceHistory p2 WHERE p2.asset.id = p.asset.id)")
    List<PriceHistory> findLatestPerAsset();

}
