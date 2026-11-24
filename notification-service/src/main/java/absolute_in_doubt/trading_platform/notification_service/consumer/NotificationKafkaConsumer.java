package absolute_in_doubt.trading_platform.notification_service.consumer;

import absolute_in_doubt.trading_platform.notification_service.entity.NotificationEntity;
import absolute_in_doubt.trading_platform.notification_service.event.*;
import absolute_in_doubt.trading_platform.notification_service.repository.NotificationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import invest.portfolio.v1.AssetHoldersRequest;
import invest.portfolio.v1.AssetHoldersResponse;
import invest.portfolio.v1.PortfolioServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final NotificationRepository notificationRepository;
    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final ObjectMapper objectMapper;

    @Value("${app.thresholds.notify-percent:5.0}")
    private double notifyPercentThreshold;

    @KafkaListener(topics = "${app.kafka.topics.trade-executed:trading.trade-executed}", groupId = "notification-group")
    @Transactional
    public void onTradeExecuted(String message) {
        try {
            EventEnvelope<TradeExecutedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<TradeExecutedPayload>>() {});
            TradeExecutedPayload p = envelope.getPayload();
            UUID eventId = UUID.fromString(envelope.getEventId());
            UUID investorId = UUID.fromString(p.getInvestorId());

            String title = "Сделка исполнена";
            String text = String.format("Сделка %s: %s шт. %s по цене %s %s. Сумма: %s %s.",
                    p.getTradeType(), p.getQuantity(), p.getTicker(), p.getPrice(), p.getCurrency(), p.getTotalAmount(), p.getCurrency());

            createNotificationIfNotExists(investorId, eventId, "TRADE_EXECUTED", title, text);

            if (p.getBrokerId() != null && !p.getBrokerId().isBlank()) {
                UUID brokerId = UUID.fromString(p.getBrokerId());
                createNotificationIfNotExists(brokerId, eventId, "TRADE_EXECUTED", title,
                        "Инвестор провел сделку: " + text);
            }
        } catch (Exception e) {
            log.error("Failed to process TradeExecuted notification", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-processed:payment.payment-processed}", groupId = "notification-group")
    @Transactional
    public void onPaymentProcessed(String message) {
        try {
            EventEnvelope<PaymentProcessedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentProcessedPayload>>() {});
            PaymentProcessedPayload p = envelope.getPayload();
            UUID eventId = UUID.fromString(envelope.getEventId());
            UUID investorId = UUID.fromString(p.getInvestorId());

            String title = "DEPOSIT".equalsIgnoreCase(p.getOperationType()) ? "Пополнение счёта" : "Вывод средств";
            String text = String.format("Платёжная операция %s: %s %s проведена успешно.",
                    p.getOperationType(), p.getAmount(), p.getCurrency());

            createNotificationIfNotExists(investorId, eventId, "PAYMENT_PROCESSED", title, text);
        } catch (Exception e) {
            log.error("Failed to process PaymentProcessed notification", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.asset-price-updated:asset.price-updated}", groupId = "notification-group")
    @Transactional
    public void onAssetPriceUpdated(String message) {
        try {
            EventEnvelope<AssetPriceUpdatedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<AssetPriceUpdatedPayload>>() {});
            AssetPriceUpdatedPayload p = envelope.getPayload();

            // Ignore initial price setup where oldPrice is null
            if (p.getOldPrice() == null || p.getChangePercent() == null) {
                return;
            }

            BigDecimal change = new BigDecimal(p.getChangePercent()).abs();
            if (change.compareTo(BigDecimal.valueOf(notifyPercentThreshold)) < 0) {
                return; // Change below 5% threshold
            }

            UUID eventId = UUID.fromString(envelope.getEventId());
            AssetHoldersResponse holdersResp = portfolioServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getAssetHolders(AssetHoldersRequest.newBuilder().setAssetId(p.getAssetId()).build());

            String title = "Значительное изменение цены актива";
            String text = String.format("Цена актива %s (%s) изменилась на %s%%: новая цена %s %s.",
                    p.getTicker(), p.getCategoryName(), p.getChangePercent(), p.getNewPrice(), p.getCurrency());

            for (String investorIdStr : holdersResp.getInvestorIdsList()) {
                UUID investorId = UUID.fromString(investorIdStr);
                createNotificationIfNotExists(investorId, eventId, "ASSET_PRICE_CHANGE", title, text);
            }
        } catch (Exception e) {
            log.error("Failed to process AssetPriceUpdated notification", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.portfolio-value-changed:portfolio.value-changed}", groupId = "notification-group")
    @Transactional
    public void onPortfolioValueChanged(String message) {
        try {
            EventEnvelope<PortfolioValueChangedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PortfolioValueChangedPayload>>() {});
            PortfolioValueChangedPayload p = envelope.getPayload();

            BigDecimal change = new BigDecimal(p.getChangePercent()).abs();
            if (change.compareTo(BigDecimal.valueOf(notifyPercentThreshold)) < 0) {
                return; // Below 5% threshold
            }

            UUID eventId = UUID.fromString(envelope.getEventId());
            UUID investorId = UUID.fromString(p.getInvestorId());

            String title = "Значительное изменение стоимости портфеля";
            String text = String.format("Стоимость вашего портфеля «%s» изменилась на %s%%: текущая оценка %s %s.",
                    p.getPortfolioName(), p.getChangePercent(), p.getCurrentValue(), p.getCurrency());

            createNotificationIfNotExists(investorId, eventId, "PORTFOLIO_VALUE_CHANGE", title, text);
        } catch (Exception e) {
            log.error("Failed to process PortfolioValueChanged notification", e);
        }
    }

    private void createNotificationIfNotExists(UUID userId, UUID eventId, String type, String title, String text) {
        if (eventId != null && notificationRepository.existsBySourceEventIdAndUserId(eventId, userId)) {
            return;
        }

        NotificationEntity n = NotificationEntity.builder()
                .userId(userId)
                .sourceEventId(eventId)
                .notificationType(type)
                .title(title)
                .message(text)
                .deliveryStatus("SENT")
                .isRead(false)
                .build();
        notificationRepository.save(n);
        log.info("Created notification for user {}: {}", userId, title);
    }
}
