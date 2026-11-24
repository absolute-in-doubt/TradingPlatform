package absolute_in_doubt.trading_platform.analytics_service.repository;

import absolute_in_doubt.trading_platform.analytics_service.entity.PriceHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntity, Long> {
    boolean existsByEventId(UUID eventId);

    @Query("SELECT ph FROM PriceHistoryEntity ph WHERE ph.assetId = :assetId AND ph.changedAt <= :date ORDER BY ph.changedAt DESC LIMIT 1")
    Optional<PriceHistoryEntity> findLatestPriceBeforeOrAt(@Param("assetId") UUID assetId, @Param("date") Instant date);
}
