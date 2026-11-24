package absolute_in_doubt.trading_platform.analytics_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_facts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFactEntity {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(name = "operation_type", nullable = false, length = 20)
    private String operationType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
