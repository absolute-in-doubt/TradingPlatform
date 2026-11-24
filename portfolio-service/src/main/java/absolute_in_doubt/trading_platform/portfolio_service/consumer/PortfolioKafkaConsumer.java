package absolute_in_doubt.trading_platform.portfolio_service.consumer;

import absolute_in_doubt.trading_platform.portfolio_service.event.AssetPriceUpdatedPayload;
import absolute_in_doubt.trading_platform.portfolio_service.event.EventEnvelope;
import absolute_in_doubt.trading_platform.portfolio_service.event.PaymentProcessedPayload;
import absolute_in_doubt.trading_platform.portfolio_service.event.TradeExecutedPayload;
import absolute_in_doubt.trading_platform.portfolio_service.service.PortfolioManagerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioKafkaConsumer {

    private final PortfolioManagerService portfolioManagerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.trade-executed:trading.trade-executed}", groupId = "portfolio-group")
    public void onTradeExecuted(String message) {
        try {
            EventEnvelope<TradeExecutedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<TradeExecutedPayload>>() {});
            portfolioManagerService.processTradeExecuted(envelope.getPayload(), envelope.getEventId());
        } catch (Exception e) {
            log.error("Failed to process TradeExecuted message: {}", message, e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-processed:payment.payment-processed}", groupId = "portfolio-group")
    public void onPaymentProcessed(String message) {
        try {
            EventEnvelope<PaymentProcessedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentProcessedPayload>>() {});
            portfolioManagerService.processPaymentProcessed(envelope.getPayload(), envelope.getEventId());
        } catch (Exception e) {
            log.error("Failed to process PaymentProcessed message: {}", message, e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.asset-price-updated:asset.price-updated}", groupId = "portfolio-group")
    public void onAssetPriceUpdated(String message) {
        try {
            EventEnvelope<AssetPriceUpdatedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<AssetPriceUpdatedPayload>>() {});
            portfolioManagerService.processAssetPriceUpdated(envelope.getPayload());
        } catch (Exception e) {
            log.error("Failed to process AssetPriceUpdated message: {}", message, e);
        }
    }
}
