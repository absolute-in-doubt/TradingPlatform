package absolute_in_doubt.trading_platform.portfolio_service.service;

import absolute_in_doubt.trading_platform.portfolio_service.entity.*;
import absolute_in_doubt.trading_platform.portfolio_service.event.*;
import absolute_in_doubt.trading_platform.portfolio_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioManagerService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final CashAccountRepository cashAccountRepository;
    private final AssetPriceCacheRepository priceCacheRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.portfolio-value-changed:portfolio.value-changed}")
    private String portfolioValueChangedTopic;

    @Value("${app.thresholds.portfolio-publish-percent:1.0}")
    private double publishThresholdPercent;

    @Transactional
    public void processTradeExecuted(TradeExecutedPayload payload, String eventId) {
        UUID eventUuid = UUID.fromString(eventId);
        if (processedEventRepository.existsById(eventUuid)) {
            log.info("TradeExecuted event {} already processed, skipping", eventId);
            return;
        }

        UUID portfolioId = UUID.fromString(payload.getPortfolioId());
        UUID investorId = UUID.fromString(payload.getInvestorId());
        UUID assetId = UUID.fromString(payload.getAssetId());
        BigDecimal quantity = new BigDecimal(payload.getQuantity());
        BigDecimal price = new BigDecimal(payload.getPrice());
        BigDecimal totalAmount = new BigDecimal(payload.getTotalAmount());
        String currency = payload.getCurrency();

        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        CashAccountEntity cashAccount = cashAccountRepository.findByInvestorIdAndCurrency(investorId, currency)
                .orElseGet(() -> cashAccountRepository.save(CashAccountEntity.builder()
                        .investorId(investorId)
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .build()));

        PortfolioPositionEntity position = positionRepository.findByPortfolioIdAndAssetId(portfolioId, assetId)
                .orElse(null);

        if ("BUY".equalsIgnoreCase(payload.getTradeType())) {
            if (position == null) {
                position = PortfolioPositionEntity.builder()
                        .portfolio(portfolio)
                        .assetId(assetId)
                        .quantity(quantity)
                        .avgPurchasePrice(price)
                        .build();
            } else {
                BigDecimal oldQty = position.getQuantity();
                BigDecimal oldAvgPrice = position.getAvgPurchasePrice();
                BigDecimal newQty = oldQty.add(quantity);

                BigDecimal totalCost = oldQty.multiply(oldAvgPrice).add(quantity.multiply(price));
                BigDecimal newAvgPrice = totalCost.divide(newQty, 4, RoundingMode.HALF_UP);

                position.setQuantity(newQty);
                position.setAvgPurchasePrice(newAvgPrice);
            }
            positionRepository.save(position);

            cashAccount.setBalance(cashAccount.getBalance().subtract(totalAmount));
            cashAccountRepository.save(cashAccount);

            // Shift last_published_value by trade amount
            BigDecimal newPublished = portfolio.getLastPublishedValue().add(totalAmount);
            portfolio.setLastPublishedValue(newPublished);
            portfolioRepository.save(portfolio);

        } else if ("SELL".equalsIgnoreCase(payload.getTradeType())) {
            if (position != null) {
                BigDecimal newQty = position.getQuantity().subtract(quantity);
                if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                    positionRepository.delete(position);
                } else {
                    position.setQuantity(newQty);
                    positionRepository.save(position);
                }
            }

            cashAccount.setBalance(cashAccount.getBalance().add(totalAmount));
            cashAccountRepository.save(cashAccount);

            // Shift last_published_value down by trade amount
            BigDecimal newPublished = portfolio.getLastPublishedValue().subtract(totalAmount);
            if (newPublished.compareTo(BigDecimal.ZERO) < 0) {
                newPublished = BigDecimal.ZERO;
            }
            portfolio.setLastPublishedValue(newPublished);
            portfolioRepository.save(portfolio);
        }

        // Cache the price if not cached
        priceCacheRepository.findById(assetId).orElseGet(() -> priceCacheRepository.save(
                AssetPriceCacheEntity.builder()
                        .assetId(assetId)
                        .currentPrice(price)
                        .currency(currency)
                        .build()));

        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventUuid)
                .eventType("TradeExecuted")
                .processedAt(Instant.now())
                .build());

        log.info("Processed TradeExecuted for portfolio {}", portfolioId);
    }

    @Transactional
    public void processPaymentProcessed(PaymentProcessedPayload payload, String eventId) {
        UUID eventUuid = UUID.fromString(eventId);
        if (processedEventRepository.existsById(eventUuid)) {
            log.info("PaymentProcessed event {} already processed, skipping", eventId);
            return;
        }

        UUID investorId = UUID.fromString(payload.getInvestorId());
        BigDecimal amount = new BigDecimal(payload.getAmount());
        String currency = payload.getCurrency();

        CashAccountEntity cashAccount = cashAccountRepository.findByInvestorIdAndCurrency(investorId, currency)
                .orElseGet(() -> cashAccountRepository.save(CashAccountEntity.builder()
                        .investorId(investorId)
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .build()));

        if ("DEPOSIT".equalsIgnoreCase(payload.getOperationType())) {
            cashAccount.setBalance(cashAccount.getBalance().add(amount));
        } else if ("WITHDRAWAL".equalsIgnoreCase(payload.getOperationType())) {
            cashAccount.setBalance(cashAccount.getBalance().subtract(amount));
        }
        cashAccountRepository.save(cashAccount);

        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventUuid)
                .eventType("PaymentProcessed")
                .processedAt(Instant.now())
                .build());

        log.info("Processed PaymentProcessed for investor {}", investorId);
    }

    @Transactional
    public void processAssetPriceUpdated(AssetPriceUpdatedPayload payload) {
        UUID assetId = UUID.fromString(payload.getAssetId());
        BigDecimal newPrice = new BigDecimal(payload.getNewPrice());
        String currency = payload.getCurrency();

        AssetPriceCacheEntity cache = priceCacheRepository.findById(assetId)
                .orElseGet(() -> AssetPriceCacheEntity.builder().assetId(assetId).build());

        cache.setCurrentPrice(newPrice);
        cache.setCurrency(currency);
        priceCacheRepository.save(cache);

        // Find affected portfolios
        List<PortfolioPositionEntity> positions = positionRepository.findByAssetId(assetId);
        Set<UUID> affectedPortfolioIds = new HashSet<>();
        for (PortfolioPositionEntity pos : positions) {
            affectedPortfolioIds.add(pos.getPortfolio().getId());
        }

        for (UUID portfolioId : affectedPortfolioIds) {
            portfolioRepository.findById(portfolioId).ifPresent(portfolio -> {
                BigDecimal currentValue = calculateTotalPortfolioValue(portfolio);
                BigDecimal lastPublished = portfolio.getLastPublishedValue();

                if (lastPublished != null && lastPublished.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = currentValue.subtract(lastPublished).abs();
                    BigDecimal changePercent = change.divide(lastPublished, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    if (changePercent.compareTo(BigDecimal.valueOf(publishThresholdPercent)) >= 0) {
                        BigDecimal signedChangePercent = currentValue.subtract(lastPublished)
                                .divide(lastPublished, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));

                        portfolio.setLastPublishedValue(currentValue);
                        portfolioRepository.save(portfolio);

                        PortfolioValueChangedPayload changePayload = PortfolioValueChangedPayload.builder()
                                .portfolioId(portfolio.getId().toString())
                                .portfolioName(portfolio.getName())
                                .investorId(portfolio.getInvestorId().toString())
                                .previousValue(lastPublished.toPlainString())
                                .currentValue(currentValue.toPlainString())
                                .changePercent(signedChangePercent.setScale(2, RoundingMode.HALF_UP).toPlainString())
                                .currency(portfolio.getBaseCurrency())
                                .calculatedAt(Instant.now().toString())
                                .build();

                        EventEnvelope<PortfolioValueChangedPayload> envelope = EventEnvelope.<PortfolioValueChangedPayload>builder()
                                .eventType("PortfolioValueChanged")
                                .producer("portfolio-service")
                                .payload(changePayload)
                                .build();

                        kafkaTemplate.send(portfolioValueChangedTopic, portfolio.getId().toString(), envelope);
                        log.info("Published PortfolioValueChanged for portfolio {}", portfolioId);
                    }
                } else {
                    portfolio.setLastPublishedValue(currentValue);
                    portfolioRepository.save(portfolio);
                }
            });
        }
    }

    public BigDecimal calculateTotalPortfolioValue(PortfolioEntity portfolio) {
        BigDecimal total = BigDecimal.ZERO;
        List<PortfolioPositionEntity> positions = positionRepository.findByPortfolioId(portfolio.getId());
        for (PortfolioPositionEntity pos : positions) {
            BigDecimal price = priceCacheRepository.findById(pos.getAssetId())
                    .map(AssetPriceCacheEntity::getCurrentPrice)
                    .orElse(pos.getAvgPurchasePrice());
            BigDecimal marketValue = pos.getQuantity().multiply(price);
            total = total.add(marketValue);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
