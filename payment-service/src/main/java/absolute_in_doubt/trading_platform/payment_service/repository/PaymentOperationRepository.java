package absolute_in_doubt.trading_platform.payment_service.repository;

import absolute_in_doubt.trading_platform.payment_service.entity.PaymentOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentOperationRepository extends JpaRepository<PaymentOperationEntity, UUID>, JpaSpecificationExecutor<PaymentOperationEntity> {
}
