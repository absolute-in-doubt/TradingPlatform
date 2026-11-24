package absolute_in_doubt.trading_platform.portfolio_service.repository;

import absolute_in_doubt.trading_platform.portfolio_service.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {
}
