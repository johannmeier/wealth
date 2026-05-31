package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinMetal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CoinRepository extends JpaRepository<Coin, Long> {
    List<Coin> findAllByOrderByMetalAscNameAscMintYearAsc();
    List<Coin> findByMetal(CoinMetal metal);
}
