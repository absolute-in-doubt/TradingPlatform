package absolute_in_doubt.trading_platform.auth_service.repository;

import absolute_in_doubt.trading_platform.auth_service.entity.BrokerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BrokerProfileRepository extends JpaRepository<BrokerProfileEntity, UUID> {
}
