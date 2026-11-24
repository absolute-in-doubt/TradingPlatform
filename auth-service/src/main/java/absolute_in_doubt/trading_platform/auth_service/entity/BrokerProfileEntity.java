package absolute_in_doubt.trading_platform.auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "broker_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrokerProfileEntity {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(length = 100)
    private String specialization;

    @Column(name = "max_investors")
    @Builder.Default
    private Integer maxInvestors = 50;
}
