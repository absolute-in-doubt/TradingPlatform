package absolute_in_doubt.trading_platform.notification_service.grpc;

import absolute_in_doubt.trading_platform.notification_service.entity.NotificationEntity;
import absolute_in_doubt.trading_platform.notification_service.repository.NotificationRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.notification.v1.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class NotificationGrpcService extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public void listNotifications(ListNotificationsRequest request, StreamObserver<ListNotificationsResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            int offset = Math.max(0, request.getOffset());
            int pageNumber = offset / limit;

            PageRequest pageRequest = PageRequest.of(pageNumber, limit, Sort.by("createdAt").descending());
            Page<NotificationEntity> page;

            if (request.getUnreadOnly()) {
                page = notificationRepository.findByUserIdAndIsReadFalse(userId, pageRequest);
            } else {
                page = notificationRepository.findByUserId(userId, pageRequest);
            }

            ListNotificationsResponse.Builder builder = ListNotificationsResponse.newBuilder()
                    .setTotalCount((int) page.getTotalElements());

            for (NotificationEntity n : page.getContent()) {
                builder.addItems(mapToNotificationResponse(n));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in listNotifications", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void countUnread(CountUnreadRequest request, StreamObserver<CountUnreadResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            int count = notificationRepository.countByUserIdAndIsReadFalse(userId);

            responseObserver.onNext(CountUnreadResponse.newBuilder().setCount(count).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in countUnread", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void markRead(MarkReadRequest request, StreamObserver<NotificationResponse> responseObserver) {
        try {
            UUID notificationId = UUID.fromString(request.getNotificationId());
            UUID userId = UUID.fromString(request.getUserId());

            NotificationEntity notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Notification not found: " + notificationId).asRuntimeException());

            if (!notification.getUserId().equals(userId)) {
                responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Cannot mark notification of another user").asRuntimeException());
                return;
            }

            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            NotificationEntity saved = notificationRepository.save(notification);

            responseObserver.onNext(mapToNotificationResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in markRead", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void markAllRead(MarkAllReadRequest request, StreamObserver<MarkAllReadResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            int updated = notificationRepository.markAllAsRead(userId, Instant.now());

            responseObserver.onNext(MarkAllReadResponse.newBuilder().setUpdatedCount(updated).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in markAllRead", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private NotificationResponse mapToNotificationResponse(NotificationEntity n) {
        NotificationResponse.Builder b = NotificationResponse.newBuilder()
                .setId(n.getId().toString())
                .setUserId(n.getUserId().toString())
                .setTitle(n.getTitle())
                .setMessage(n.getMessage())
                .setIsRead(Boolean.TRUE.equals(n.getIsRead()))
                .setCreatedAt(n.getCreatedAt() != null ? n.getCreatedAt().toString() : Instant.now().toString())
                .setReadAt(n.getReadAt() != null ? n.getReadAt().toString() : "");

        try {
            b.setType(NotificationType.valueOf(n.getNotificationType()));
        } catch (Exception e) {
            b.setType(NotificationType.NOTIFICATION_TYPE_UNSPECIFIED);
        }

        try {
            b.setDeliveryStatus(DeliveryStatus.valueOf(n.getDeliveryStatus()));
        } catch (Exception e) {
            b.setDeliveryStatus(DeliveryStatus.SENT);
        }

        return b.build();
    }
}
