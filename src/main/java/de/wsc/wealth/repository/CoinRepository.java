package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.Depot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CoinRepository extends JpaRepository<Coin, Long> {
    Optional<Coin> findFirstByName(String name);
    List<Coin> findAllByOrderByMetalAscNameAscMintYearAsc();
    List<Coin> findByMetal(CoinMetal metal);
    List<Coin> findByDepotOrderByMetalAscNameAscMintYearAsc(Depot depot);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c.name FROM Coin c ORDER BY c.name")
    List<String> findDistinctNames();
    boolean existsByAsset(de.wsc.wealth.domain.Asset asset);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Coin c LEFT JOIN FETCH c.depot")
    List<Coin> findAllWithDepot();

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Coin c LEFT JOIN FETCH c.asset ORDER BY c.metal, c.name, c.mintYear")
    List<Coin> findAllWithAsset();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c.asset.id FROM Coin c WHERE c.asset IS NOT NULL")
    java.util.Set<Long> findDistinctLinkedAssetIds();
}
