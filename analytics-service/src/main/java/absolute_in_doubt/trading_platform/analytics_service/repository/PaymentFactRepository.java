package absolute_in_doubt.trading_platform.analytics_service.repository;

import absolute_in_doubt.trading_platform.analytics_service.entity.PaymentFactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface PaymentFactRepository extends JpaRepository<PaymentFactEntity, UUID> {
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentFactEntity p WHERE p.operationType = 'DEPOSIT' AND p.processedAt BETWEEN :start AND :end")
    BigDecimal sumDepositsInPeriod(@Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentFactEntity p WHERE p.operationType = 'WITHDRAWAL' AND p.processedAt BETWEEN :start AND :end")
    BigDecimal sumWithdrawalsInPeriod(@Param("start") Instant start, @Param("end") Instant end);
}
