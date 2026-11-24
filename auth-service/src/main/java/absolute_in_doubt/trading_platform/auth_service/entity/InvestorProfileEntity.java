package absolute_in_doubt.trading_platform.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investor_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestorProfileEntity {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "assigned_broker_id")
    private UUID assignedBrokerId;

    @Column(name = "risk_profile", nullable = false, length = 20)
    @Builder.Default
    private String riskProfile = "MODERATE";

    @Column(name = "investment_experience_years")
    @Builder.Default
    private Integer investmentExperienceYears = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
