package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import de.wsc.wealth.domain.Depot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CoinRepository extends JpaRepository<Coin, Long> {
    List<Coin> findAllByOrderByMetalAscNameAscMintYearAsc();
    List<Coin> findByMetal(CoinMetal metal);
    List<Coin> findByDepotOrderByMetalAscNameAscMintYearAsc(Depot depot);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c.name FROM Coin c ORDER BY c.name")
    List<String> findDistinctNames();
}
