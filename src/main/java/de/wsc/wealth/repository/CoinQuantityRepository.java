package de.wsc.wealth.repository;

import de.wsc.wealth.domain.Coin;
import de.wsc.wealth.domain.CoinQuantity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CoinQuantityRepository extends JpaRepository<CoinQuantity, Long> {
    List<CoinQuantity> findByCoinOrderByDateDesc(Coin coin);
    Optional<CoinQuantity> findFirstByCoinOrderByDateDesc(Coin coin);

    @Query("SELECT cq FROM CoinQuantity cq JOIN FETCH cq.coin")
    List<CoinQuantity> findAllWithCoin();
}
