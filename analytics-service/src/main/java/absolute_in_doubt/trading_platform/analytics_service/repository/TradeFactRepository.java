package absolute_in_doubt.trading_platform.analytics_service.repository;

import absolute_in_doubt.trading_platform.analytics_service.entity.TradeFactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TradeFactRepository extends JpaRepository<TradeFactEntity, UUID> {
    List<TradeFactEntity> findByPortfolioIdAndExecutedAtBetween(UUID portfolioId, Instant start, Instant end);

    @Query("SELECT COUNT(t) FROM TradeFactEntity t WHERE t.executedAt BETWEEN :start AND :end")
    long countTradesInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(t.totalAmount), 0) FROM TradeFactEntity t WHERE t.executedAt BETWEEN :start AND :end")
    BigDecimal sumTradeVolumeInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COUNT(DISTINCT t.investorId) FROM TradeFactEntity t WHERE t.executedAt BETWEEN :start AND :end")
    long countDistinctInvestorsInPeriod(@Param("start") Instant start, @Param("end") Instant end);
}
