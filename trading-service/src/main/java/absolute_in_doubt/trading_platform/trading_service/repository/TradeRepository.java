package absolute_in_doubt.trading_platform.trading_service.repository;

import absolute_in_doubt.trading_platform.trading_service.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<TradeEntity, UUID>, JpaSpecificationExecutor<TradeEntity> {
}
