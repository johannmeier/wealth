package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByArchivedFalseAndCategory(AssetCategory category);
    List<Asset> findByArchivedFalseAndSymbolIsNotNull();
    List<Asset> findAllByArchivedFalseOrderByNameAsc();
    List<Asset> findAllByArchivedTrueOrderByNameAsc();
    java.util.Optional<Asset> findFirstByArchivedTrueAndIsin(String isin);
    java.util.Optional<Asset> findFirstByArchivedTrueAndSymbol(String symbol);
}
