package absolute_in_doubt.trading_platform.analytics_service.grpc;

import absolute_in_doubt.trading_platform.analytics_service.entity.*;
import absolute_in_doubt.trading_platform.analytics_service.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.analytics.v1.*;
import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.portfolio.v1.PositionSnapshot;
import invest.portfolio.v1.SnapshotRequest;
import invest.portfolio.v1.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AnalyticsGrpcService extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {

    private final ReportRepository reportRepository;
    private final TradeFactRepository tradeFactRepository;
    private final PaymentFactRepository paymentFactRepository;
    private final AssetRefRepository assetRefRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PortfolioValueRepository portfolioValueRepository;
    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public void getPerformance(GetPerformanceRequest request, StreamObserver<PortfolioPerformanceResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            LocalDate startLocal = LocalDate.parse(request.getPeriodStart());
            LocalDate endLocal = LocalDate.parse(request.getPeriodEnd());
            Instant start = startLocal.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = endLocal.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            // Fetch live snapshot from portfolio-service
            SnapshotResponse snapshot;
            try {
                snapshot = portfolioServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getPortfolioSnapshot(SnapshotRequest.newBuilder().setPortfolioId(portfolioId.toString()).build());
            } catch (Exception e) {
                log.error("Failed to call PortfolioService for snapshot", e);
                responseObserver.onError(Status.UNAVAILABLE.withDescription("Portfolio Service is temporarily unavailable").asRuntimeException());
                return;
            }

            BigDecimal endValue = snapshot.getFound() ? new BigDecimal(snapshot.getTotalValue()) : BigDecimal.ZERO;
            String currency = snapshot.getFound() ? snapshot.getBaseCurrency() : "USD";

            List<TradeFactEntity> trades = tradeFactRepository.findByPortfolioIdAndExecutedAtBetween(portfolioId, start, end);
            BigDecimal purchases = BigDecimal.ZERO;
            BigDecimal sales = BigDecimal.ZERO;

            for (TradeFactEntity t : trades) {
                if ("BUY".equalsIgnoreCase(t.getTradeType())) {
                    purchases = purchases.add(t.getTotalAmount());
                } else if ("SELL".equalsIgnoreCase(t.getTradeType())) {
                    sales = sales.add(t.getTotalAmount());
                }
            }

            // Estimate startValue: endValue - purchases + sales (non-negative)
            BigDecimal startValue = endValue.subtract(purchases).add(sales);
            if (startValue.compareTo(BigDecimal.ZERO) < 0) {
                startValue = BigDecimal.ZERO;
            }

            BigDecimal profit = endValue.subtract(startValue).subtract(purchases).add(sales);
            BigDecimal denominator = startValue.add(purchases);
            BigDecimal yieldPercent = BigDecimal.ZERO;
            if (denominator.compareTo(BigDecimal.ZERO) > 0) {
                yieldPercent = profit.divide(denominator, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            PortfolioPerformanceResponse response = PortfolioPerformanceResponse.newBuilder()
                    .setPortfolioId(portfolioId.toString())
                    .setPeriodStart(request.getPeriodStart())
                    .setPeriodEnd(request.getPeriodEnd())
                    .setCurrency(currency)
                    .setStartValue(startValue.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setEndValue(endValue.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setPurchasesAmount(purchases.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setSalesAmount(sales.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setProfit(profit.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setYieldPercent(yieldPercent.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getPerformance", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void generateReport(GenerateReportRequest request, StreamObserver<ReportResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            LocalDate startLocal = LocalDate.parse(request.getPeriodStart());
            LocalDate endLocal = LocalDate.parse(request.getPeriodEnd());
            Instant start = startLocal.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = endLocal.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            SnapshotResponse snapshot = portfolioServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getPortfolioSnapshot(SnapshotRequest.newBuilder().setPortfolioId(portfolioId.toString()).build());

            BigDecimal endValue = snapshot.getFound() ? new BigDecimal(snapshot.getTotalValue()) : BigDecimal.ZERO;
            String currency = snapshot.getFound() ? snapshot.getBaseCurrency() : "USD";

            List<TradeFactEntity> trades = tradeFactRepository.findByPortfolioIdAndExecutedAtBetween(portfolioId, start, end);
            BigDecimal purchases = BigDecimal.ZERO;
            BigDecimal sales = BigDecimal.ZERO;

            for (TradeFactEntity t : trades) {
                if ("BUY".equalsIgnoreCase(t.getTradeType())) {
                    purchases = purchases.add(t.getTotalAmount());
                } else if ("SELL".equalsIgnoreCase(t.getTradeType())) {
                    sales = sales.add(t.getTotalAmount());
                }
            }

            BigDecimal startValue = endValue.subtract(purchases).add(sales);
            if (startValue.compareTo(BigDecimal.ZERO) < 0) {
                startValue = BigDecimal.ZERO;
            }

            BigDecimal profit = endValue.subtract(startValue).subtract(purchases).add(sales);
            BigDecimal denominator = startValue.add(purchases);
            BigDecimal yieldPercent = BigDecimal.ZERO;
            if (denominator.compareTo(BigDecimal.ZERO) > 0) {
                yieldPercent = profit.divide(denominator, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            BigDecimal netCashFlow = purchases.subtract(sales);

            // Category breakdown
            Map<String, BigDecimal> categoryValues = new HashMap<>();
            BigDecimal totalCatValue = BigDecimal.ZERO;

            if (snapshot.getFound()) {
                for (PositionSnapshot p : snapshot.getPositionsList()) {
                    UUID assetId = UUID.fromString(p.getAssetId());
                    String categoryName = assetRefRepository.findById(assetId)
                            .map(AssetRefEntity::getCategoryName)
                            .orElse("Other");
                    BigDecimal val = new BigDecimal(p.getMarketValue());
                    categoryValues.put(categoryName, categoryValues.getOrDefault(categoryName, BigDecimal.ZERO).add(val));
                    totalCatValue = totalCatValue.add(val);
                }
            }

            List<CategoryShareResponse> structure = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : categoryValues.entrySet()) {
                BigDecimal val = entry.getValue();
                BigDecimal share = BigDecimal.ZERO;
                if (totalCatValue.compareTo(BigDecimal.ZERO) > 0) {
                    share = val.divide(totalCatValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                structure.add(CategoryShareResponse.newBuilder()
                        .setCategory(entry.getKey())
                        .setValue(val.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .setShare(share.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .build());
            }

            // Save report
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("startValue", startValue.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("endValue", endValue.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("purchasesAmount", purchases.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("salesAmount", sales.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("netCashFlow", netCashFlow.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("profit", profit.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("yieldPercent", yieldPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
            contentMap.put("currency", currency);
            contentMap.put("structure", structure.stream().map(s -> Map.of(
                    "category", s.getCategory(),
                    "value", s.getValue(),
                    "share", s.getShare()
            )).toList());

            ReportEntity report = ReportEntity.builder()
                    .portfolioId(portfolioId)
                    .reportType(request.getType().name())
                    .periodFrom(startLocal)
                    .periodTo(endLocal)
                    .contentJson(objectMapper.writeValueAsString(contentMap))
                    .build();

            ReportEntity saved = reportRepository.save(report);

            ReportResponse response = mapToReportResponse(saved, contentMap);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in generateReport", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listReports(ListReportsRequest request, StreamObserver<ListReportsResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            int offset = Math.max(0, request.getOffset());
            int pageNumber = offset / limit;

            Page<ReportEntity> page = reportRepository.findByPortfolioId(portfolioId,
                    PageRequest.of(pageNumber, limit, Sort.by("generatedAt").descending()));

            ListReportsResponse.Builder builder = ListReportsResponse.newBuilder()
                    .setTotalCount((int) page.getTotalElements());

            for (ReportEntity r : page.getContent()) {
                Map<String, Object> content = objectMapper.readValue(r.getContentJson(), new TypeReference<Map<String, Object>>() {});
                builder.addItems(mapToReportResponse(r, content));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in listReports", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getReport(GetReportRequest request, StreamObserver<ReportResponse> responseObserver) {
        try {
            UUID reportId = UUID.fromString(request.getReportId());
            ReportEntity report = reportRepository.findById(reportId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Report not found: " + reportId).asRuntimeException());

            Map<String, Object> content = objectMapper.readValue(report.getContentJson(), new TypeReference<Map<String, Object>>() {});
            responseObserver.onNext(mapToReportResponse(report, content));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getReport", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getPlatformStats(GetPlatformStatsRequest request, StreamObserver<PlatformStatsResponse> responseObserver) {
        try {
            LocalDate startLocal = LocalDate.parse(request.getPeriodStart());
            LocalDate endLocal = LocalDate.parse(request.getPeriodEnd());
            Instant start = startLocal.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = endLocal.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            long investorsCount = tradeFactRepository.countDistinctInvestorsInPeriod(start, end);
            long tradesCount = tradeFactRepository.countTradesInPeriod(start, end);
            BigDecimal tradesVolume = tradeFactRepository.sumTradeVolumeInPeriod(start, end);
            BigDecimal deposits = paymentFactRepository.sumDepositsInPeriod(start, end);
            BigDecimal withdrawals = paymentFactRepository.sumWithdrawalsInPeriod(start, end);
            BigDecimal totalPortfolioValue = portfolioValueRepository.sumTotalPortfolioValues();
            long portfoliosCount = portfolioValueRepository.count();

            // Structure by category across all assets
            List<AssetRefEntity> allAssets = assetRefRepository.findAll();
            Map<String, BigDecimal> catCounts = new HashMap<>();
            BigDecimal totalCatValue = BigDecimal.ZERO;
            for (AssetRefEntity a : allAssets) {
                catCounts.put(a.getCategoryName(), catCounts.getOrDefault(a.getCategoryName(), BigDecimal.ZERO).add(a.getCurrentPrice()));
                totalCatValue = totalCatValue.add(a.getCurrentPrice());
            }

            List<CategoryShareResponse> structure = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> e : catCounts.entrySet()) {
                BigDecimal share = BigDecimal.ZERO;
                if (totalCatValue.compareTo(BigDecimal.ZERO) > 0) {
                    share = e.getValue().divide(totalCatValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                }
                structure.add(CategoryShareResponse.newBuilder()
                        .setCategory(e.getKey())
                        .setValue(e.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .setShare(share.setScale(2, RoundingMode.HALF_UP).toPlainString())
                        .build());
            }

            PlatformStatsResponse response = PlatformStatsResponse.newBuilder()
                    .setPeriodStart(request.getPeriodStart())
                    .setPeriodEnd(request.getPeriodEnd())
                    .setInvestorsCount((int) Math.max(1, investorsCount))
                    .setPortfoliosCount((int) portfoliosCount)
                    .setTradesCount((int) tradesCount)
                    .setTradesVolume(tradesVolume.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setDepositsVolume(deposits.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setWithdrawalsVolume(withdrawals.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .setTotalPortfolioValue(totalPortfolioValue.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .addAllStructure(structure)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in getPlatformStats", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ReportResponse mapToReportResponse(ReportEntity r, Map<String, Object> content) {
        ReportResponse.Builder b = ReportResponse.newBuilder()
                .setId(r.getId().toString())
                .setPortfolioId(r.getPortfolioId().toString())
                .setPeriodStart(r.getPeriodFrom().toString())
                .setPeriodEnd(r.getPeriodTo().toString())
                .setGeneratedAt(r.getGeneratedAt() != null ? r.getGeneratedAt().toString() : Instant.now().toString())
                .setCurrency((String) content.getOrDefault("currency", "USD"))
                .setStartValue((String) content.getOrDefault("startValue", "0.00"))
                .setEndValue((String) content.getOrDefault("endValue", "0.00"))
                .setPurchasesAmount((String) content.getOrDefault("purchasesAmount", "0.00"))
                .setSalesAmount((String) content.getOrDefault("salesAmount", "0.00"))
                .setNetCashFlow((String) content.getOrDefault("netCashFlow", "0.00"))
                .setProfit((String) content.getOrDefault("profit", "0.00"))
                .setYieldPercent((String) content.getOrDefault("yieldPercent", "0.00"));

        try {
            b.setType(ReportType.valueOf(r.getReportType()));
        } catch (Exception e) {
            b.setType(ReportType.ON_DEMAND);
        }

        Object structObj = content.get("structure");
        if (structObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    b.addStructure(CategoryShareResponse.newBuilder()
                            .setCategory(String.valueOf(m.get("category")))
                            .setValue(String.valueOf(m.get("value")))
                            .setShare(String.valueOf(m.get("share")))
                            .build());
                }
            }
        }

        return b.build();
    }
}
