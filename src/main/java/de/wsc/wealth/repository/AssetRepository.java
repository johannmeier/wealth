package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Asset;
import de.wsc.wealth.domain.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByArchivedFalseAndCategory(AssetCategory category);
    List<Asset> findByArchivedFalseAndSymbolIsNotNull();

    @Query("SELECT a FROM Asset a WHERE a.archived = false ORDER BY LOWER(a.name)")
    List<Asset> findAllByArchivedFalseOrderByNameAsc();

    @Query("SELECT a FROM Asset a WHERE a.archived = true ORDER BY LOWER(a.name)")
    List<Asset> findAllByArchivedTrueOrderByNameAsc();
    java.util.Optional<Asset> findFirstByArchivedTrueAndIsin(String isin);
    java.util.Optional<Asset> findFirstByArchivedTrueAndSymbol(String symbol);
    java.util.Optional<Asset> findFirstByNameAndArchivedFalse(String name);
    java.util.Optional<Asset> findFirstByIsinAndArchivedFalse(String isin);
}
