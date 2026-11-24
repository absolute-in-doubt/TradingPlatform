package absolute_in_doubt.trading_platform.trading_service.grpc;

import absolute_in_doubt.trading_platform.trading_service.entity.TradeEntity;
import absolute_in_doubt.trading_platform.trading_service.event.EventEnvelope;
import absolute_in_doubt.trading_platform.trading_service.event.TradeExecutedPayload;
import absolute_in_doubt.trading_platform.trading_service.repository.TradeRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.asset.v1.AssetServiceGrpc;
import invest.asset.v1.GetAssetRequest;
import invest.asset.v1.GetAssetResponse;
import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.portfolio.v1.ValidateTradeRequest;
import invest.portfolio.v1.ValidateTradeResponse;
import invest.trading.v1.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class TradingGrpcService extends TradingServiceGrpc.TradingServiceImplBase {

    private final TradeRepository tradeRepository;
    private final AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub;
    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.trade-executed:trading.trade-executed}")
    private String tradeExecutedTopic;

    @Override
    @Transactional
    public void registerTrade(RegisterTradeRequest request, StreamObserver<TradeResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            UUID investorId = UUID.fromString(request.getInvestorId());
            UUID assetId = UUID.fromString(request.getAssetId());
            UUID brokerId = !request.getBrokerId().isBlank() ? UUID.fromString(request.getBrokerId()) : null;
            BigDecimal quantity = new BigDecimal(request.getQuantity());

            // 1. Fetch Asset
            GetAssetResponse assetResp;
            try {
                assetResp = assetServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getAsset(GetAssetRequest.newBuilder().setAssetId(request.getAssetId()).build());
            } catch (Exception e) {
                log.error("Failed to call AssetService", e);
                responseObserver.onError(Status.UNAVAILABLE.withDescription("Asset Service is temporarily unavailable").asRuntimeException());
                return;
            }

            if (!assetResp.getFound() || !assetResp.getIsActive()) {
                TradeEntity rejectedTrade = TradeEntity.builder()
                        .portfolioId(portfolioId)
                        .investorId(investorId)
                        .brokerId(brokerId)
                        .assetId(assetId)
                        .tradeType(request.getTradeType().name())
                        .quantity(quantity)
                        .price(BigDecimal.ZERO)
                        .totalAmount(BigDecimal.ZERO)
                        .currency("USD")
                        .status("REJECTED")
                        .rejectReason("ASSET_NOT_FOUND_OR_INACTIVE")
                        .executedAt(Instant.now())
                        .build();
                TradeEntity saved = tradeRepository.save(rejectedTrade);
                responseObserver.onNext(mapToTradeResponse(saved));
                responseObserver.onCompleted();
                return;
            }

            BigDecimal price = new BigDecimal(assetResp.getCurrentPrice());
            String currency = assetResp.getCurrency();
            BigDecimal totalAmount = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);

            // 2. Validate Trade with Portfolio Service
            invest.portfolio.v1.TradeType portTradeType = request.getTradeType() == TradeType.BUY
                    ? invest.portfolio.v1.TradeType.TRADE_TYPE_BUY
                    : invest.portfolio.v1.TradeType.TRADE_TYPE_SELL;

            ValidateTradeResponse validateResp;
            try {
                validateResp = portfolioServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .validateTrade(ValidateTradeRequest.newBuilder()
                                .setPortfolioId(portfolioId.toString())
                                .setInvestorId(investorId.toString())
                                .setAssetId(assetId.toString())
                                .setTradeType(portTradeType)
                                .setQuantity(quantity.toPlainString())
                                .setTotalAmount(totalAmount.toPlainString())
                                .setCurrency(currency)
                                .build());
            } catch (Exception e) {
                log.error("Failed to call PortfolioService", e);
                responseObserver.onError(Status.UNAVAILABLE.withDescription("Portfolio Service is temporarily unavailable").asRuntimeException());
                return;
            }

            if (!validateResp.getValid()) {
                TradeEntity rejectedTrade = TradeEntity.builder()
                        .portfolioId(portfolioId)
                        .investorId(investorId)
                        .brokerId(brokerId)
                        .assetId(assetId)
                        .tradeType(request.getTradeType().name())
                        .quantity(quantity)
                        .price(price)
                        .totalAmount(totalAmount)
                        .currency(currency)
                        .status("REJECTED")
                        .rejectReason(validateResp.getRejectCode().name())
                        .executedAt(Instant.now())
                        .build();
                TradeEntity saved = tradeRepository.save(rejectedTrade);
                responseObserver.onNext(mapToTradeResponse(saved));
                responseObserver.onCompleted();
                return;
            }

            // 3. Trade is valid, record EXECUTED trade
            TradeEntity trade = TradeEntity.builder()
                    .portfolioId(portfolioId)
                    .investorId(investorId)
                    .brokerId(brokerId)
                    .assetId(assetId)
                    .tradeType(request.getTradeType().name())
                    .quantity(quantity)
                    .price(price)
                    .totalAmount(totalAmount)
                    .currency(currency)
                    .status("EXECUTED")
                    .rejectReason(null)
                    .executedAt(Instant.now())
                    .build();

            TradeEntity savedTrade = tradeRepository.save(trade);

            // 4. Publish TradeExecuted event
            TradeExecutedPayload payload = TradeExecutedPayload.builder()
                    .tradeId(savedTrade.getId().toString())
                    .portfolioId(savedTrade.getPortfolioId().toString())
                    .investorId(savedTrade.getInvestorId().toString())
                    .brokerId(savedTrade.getBrokerId() != null ? savedTrade.getBrokerId().toString() : null)
                    .assetId(savedTrade.getAssetId().toString())
                    .ticker(assetResp.getTicker())
                    .tradeType(savedTrade.getTradeType())
                    .quantity(savedTrade.getQuantity().toPlainString())
                    .price(savedTrade.getPrice().toPlainString())
                    .totalAmount(savedTrade.getTotalAmount().toPlainString())
                    .currency(savedTrade.getCurrency())
                    .executedAt(savedTrade.getExecutedAt().toString())
                    .build();

            EventEnvelope<TradeExecutedPayload> envelope = EventEnvelope.<TradeExecutedPayload>builder()
                    .eventType("TradeExecuted")
                    .producer("trading-service")
                    .payload(payload)
                    .build();

            kafkaTemplate.send(tradeExecutedTopic, savedTrade.getPortfolioId().toString(), envelope);

            responseObserver.onNext(mapToTradeResponse(savedTrade));
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in registerTrade", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listTrades(ListTradesRequest request, StreamObserver<ListTradesResponse> responseObserver) {
        try {
            Specification<TradeEntity> spec = (root, query, cb) -> cb.conjunction();

            if (!request.getPortfolioId().isBlank()) {
                UUID pid = UUID.fromString(request.getPortfolioId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("portfolioId"), pid));
            }
            if (!request.getInvestorId().isBlank()) {
                UUID iid = UUID.fromString(request.getInvestorId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("investorId"), iid));
            }
            if (!request.getBrokerId().isBlank()) {
                UUID bid = UUID.fromString(request.getBrokerId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("brokerId"), bid));
            }
            if (!request.getAssetId().isBlank()) {
                UUID aid = UUID.fromString(request.getAssetId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("assetId"), aid));
            }
            if (request.getTradeType() != TradeType.TRADE_TYPE_UNSPECIFIED) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("tradeType"), request.getTradeType().name()));
            }
            if (request.getStatus() != TradeStatus.TRADE_STATUS_UNSPECIFIED) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), request.getStatus().name()));
            }
            if (!request.getDateFrom().isBlank()) {
                Instant from = Instant.parse(request.getDateFrom());
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("executedAt"), from));
            }
            if (!request.getDateTo().isBlank()) {
                Instant to = Instant.parse(request.getDateTo());
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("executedAt"), to));
            }

            String sortProp = request.getSortField() == TradeSortField.TRADE_SORT_AMOUNT ? "totalAmount" : "executedAt";
            Sort sort = request.getSortDirection() == SortDirection.SORT_ASC
                    ? Sort.by(sortProp).ascending()
                    : Sort.by(sortProp).descending();

            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            int offset = Math.max(0, request.getOffset());
            int pageNumber = offset / limit;

            Page<TradeEntity> page = tradeRepository.findAll(spec, PageRequest.of(pageNumber, limit, sort));

            ListTradesResponse.Builder builder = ListTradesResponse.newBuilder()
                    .setTotalCount((int) page.getTotalElements());

            for (TradeEntity t : page.getContent()) {
                builder.addItems(mapToTradeResponse(t));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in listTrades", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private TradeResponse mapToTradeResponse(TradeEntity t) {
        TradeResponse.Builder builder = TradeResponse.newBuilder()
                .setId(t.getId().toString())
                .setPortfolioId(t.getPortfolioId().toString())
                .setInvestorId(t.getInvestorId().toString())
                .setBrokerId(t.getBrokerId() != null ? t.getBrokerId().toString() : "")
                .setAssetId(t.getAssetId().toString())
                .setQuantity(t.getQuantity().toPlainString())
                .setPrice(t.getPrice().toPlainString())
                .setTotalAmount(t.getTotalAmount().toPlainString())
                .setCurrency(t.getCurrency())
                .setRejectReason(t.getRejectReason() != null ? t.getRejectReason() : "")
                .setExecutedAt(t.getExecutedAt() != null ? t.getExecutedAt().toString() : Instant.now().toString());

        try {
            builder.setTradeType(TradeType.valueOf(t.getTradeType()));
        } catch (Exception e) {
            builder.setTradeType(TradeType.TRADE_TYPE_UNSPECIFIED);
        }

        try {
            builder.setStatus(TradeStatus.valueOf(t.getStatus()));
        } catch (Exception e) {
            builder.setStatus(TradeStatus.TRADE_STATUS_UNSPECIFIED);
        }

        return builder.build();
    }
}
