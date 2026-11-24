package absolute_in_doubt.trading_platform.notification_service.repository;

import absolute_in_doubt.trading_platform.notification_service.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Page<NotificationEntity> findByUserId(UUID userId, Pageable pageable);
    Page<NotificationEntity> findByUserIdAndIsReadFalse(UUID userId, Pageable pageable);
    int countByUserIdAndIsReadFalse(UUID userId);
    boolean existsBySourceEventIdAndUserId(UUID sourceEventId, UUID userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true, n.readAt = :now WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
