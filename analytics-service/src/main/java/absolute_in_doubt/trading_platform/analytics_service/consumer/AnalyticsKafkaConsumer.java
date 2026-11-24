package absolute_in_doubt.trading_platform.analytics_service.consumer;

import absolute_in_doubt.trading_platform.analytics_service.entity.*;
import absolute_in_doubt.trading_platform.analytics_service.event.*;
import absolute_in_doubt.trading_platform.analytics_service.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsKafkaConsumer {

    private final TradeFactRepository tradeFactRepository;
    private final PaymentFactRepository paymentFactRepository;
    private final AssetRefRepository assetRefRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PortfolioValueRepository portfolioValueRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.trade-executed:trading.trade-executed}", groupId = "analytics-group")
    @Transactional
    public void onTradeExecuted(String message) {
        try {
            EventEnvelope<TradeExecutedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<TradeExecutedPayload>>() {});
            TradeExecutedPayload p = envelope.getPayload();
            UUID tradeId = UUID.fromString(p.getTradeId());

            if (!tradeFactRepository.existsById(tradeId)) {
                tradeFactRepository.save(TradeFactEntity.builder()
                        .tradeId(tradeId)
                        .portfolioId(UUID.fromString(p.getPortfolioId()))
                        .investorId(UUID.fromString(p.getInvestorId()))
                        .assetId(UUID.fromString(p.getAssetId()))
                        .tradeType(p.getTradeType())
                        .quantity(new BigDecimal(p.getQuantity()))
                        .price(new BigDecimal(p.getPrice()))
                        .totalAmount(new BigDecimal(p.getTotalAmount()))
                        .currency(p.getCurrency())
                        .executedAt(Instant.parse(p.getExecutedAt()))
                        .build());
                log.info("Saved trade fact for trade {}", tradeId);
            }
        } catch (Exception e) {
            log.error("Failed to process TradeExecuted in analytics", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-processed:payment.payment-processed}", groupId = "analytics-group")
    @Transactional
    public void onPaymentProcessed(String message) {
        try {
            EventEnvelope<PaymentProcessedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PaymentProcessedPayload>>() {});
            PaymentProcessedPayload p = envelope.getPayload();
            UUID paymentId = UUID.fromString(p.getPaymentId());

            if (!paymentFactRepository.existsById(paymentId)) {
                paymentFactRepository.save(PaymentFactEntity.builder()
                        .paymentId(paymentId)
                        .investorId(UUID.fromString(p.getInvestorId()))
                        .operationType(p.getOperationType())
                        .amount(new BigDecimal(p.getAmount()))
                        .currency(p.getCurrency())
                        .processedAt(Instant.parse(p.getProcessedAt()))
                        .build());
                log.info("Saved payment fact for payment {}", paymentId);
            }
        } catch (Exception e) {
            log.error("Failed to process PaymentProcessed in analytics", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.asset-price-updated:asset.price-updated}", groupId = "analytics-group")
    @Transactional
    public void onAssetPriceUpdated(String message) {
        try {
            EventEnvelope<AssetPriceUpdatedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<AssetPriceUpdatedPayload>>() {});
            AssetPriceUpdatedPayload p = envelope.getPayload();
            UUID assetId = UUID.fromString(p.getAssetId());
            UUID eventId = UUID.fromString(envelope.getEventId());
            BigDecimal newPrice = new BigDecimal(p.getNewPrice());

            // Update or create AssetRef
            AssetRefEntity assetRef = assetRefRepository.findById(assetId)
                    .orElseGet(() -> AssetRefEntity.builder().assetId(assetId).build());

            assetRef.setTicker(p.getTicker());
            assetRef.setCategoryName(p.getCategoryName());
            assetRef.setCurrency(p.getCurrency());
            assetRef.setCurrentPrice(newPrice);
            assetRefRepository.save(assetRef);

            // Record in price history (idempotent by eventId)
            if (!priceHistoryRepository.existsByEventId(eventId)) {
                priceHistoryRepository.save(PriceHistoryEntity.builder()
                        .assetId(assetId)
                        .price(newPrice)
                        .changedAt(Instant.parse(p.getUpdatedAt()))
                        .eventId(eventId)
                        .build());
                log.info("Recorded price history for asset {} at {}", p.getTicker(), newPrice);
            }
        } catch (Exception e) {
            log.error("Failed to process AssetPriceUpdated in analytics", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.portfolio-value-changed:portfolio.value-changed}", groupId = "analytics-group")
    @Transactional
    public void onPortfolioValueChanged(String message) {
        try {
            EventEnvelope<PortfolioValueChangedPayload> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<PortfolioValueChangedPayload>>() {});
            PortfolioValueChangedPayload p = envelope.getPayload();
            UUID portfolioId = UUID.fromString(p.getPortfolioId());
            Instant calculatedAt = Instant.parse(p.getCalculatedAt());

            PortfolioValueEntity val = portfolioValueRepository.findById(portfolioId)
                    .orElseGet(() -> PortfolioValueEntity.builder().portfolioId(portfolioId).build());

            if (val.getCalculatedAt() == null || calculatedAt.isAfter(val.getCalculatedAt())) {
                val.setTotalValue(new BigDecimal(p.getCurrentValue()));
                val.setCurrency(p.getCurrency());
                val.setCalculatedAt(calculatedAt);
                portfolioValueRepository.save(val);
                log.info("Updated portfolio value for portfolio {} to {}", portfolioId, p.getCurrentValue());
            }
        } catch (Exception e) {
            log.error("Failed to process PortfolioValueChanged in analytics", e);
        }
    }
}
