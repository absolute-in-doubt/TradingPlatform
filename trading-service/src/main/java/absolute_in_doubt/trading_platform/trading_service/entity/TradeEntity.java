package absolute_in_doubt.trading_platform.trading_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "investor_id", nullable = false)
    private UUID investorId;

    @Column(name = "broker_id")
    private UUID brokerId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "trade_type", nullable = false, length = 10)
    private String tradeType; // BUY | SELL

    @Column(nullable = false, precision = 14, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status; // EXECUTED | REJECTED

    @Column(name = "reject_reason", length = 100)
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;
}
