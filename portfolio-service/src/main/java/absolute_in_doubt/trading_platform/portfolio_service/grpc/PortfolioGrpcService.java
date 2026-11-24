package absolute_in_doubt.trading_platform.portfolio_service.grpc;

import absolute_in_doubt.trading_platform.portfolio_service.entity.*;
import absolute_in_doubt.trading_platform.portfolio_service.repository.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.portfolio.v1.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class PortfolioGrpcService extends PortfolioServiceGrpc.PortfolioServiceImplBase {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final CashAccountRepository cashAccountRepository;
    private final AssetPriceCacheRepository priceCacheRepository;

    @Override
    @Transactional(readOnly = true)
    public void validateTrade(ValidateTradeRequest request, StreamObserver<ValidateTradeResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            UUID investorId = UUID.fromString(request.getInvestorId());
            UUID assetId = UUID.fromString(request.getAssetId());
            BigDecimal quantity = new BigDecimal(request.getQuantity());
            BigDecimal totalAmount = new BigDecimal(request.getTotalAmount());
            String currency = request.getCurrency();

            Optional<PortfolioEntity> portfolioOpt = portfolioRepository.findById(portfolioId);
            if (portfolioOpt.isEmpty() || !portfolioOpt.get().getInvestorId().equals(investorId)) {
                responseObserver.onNext(ValidateTradeResponse.newBuilder()
                        .setValid(false)
                        .setRejectCode(RejectCode.PORTFOLIO_NOT_FOUND)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            PortfolioEntity portfolio = portfolioOpt.get();
            if (!portfolio.getBaseCurrency().equalsIgnoreCase(currency)) {
                responseObserver.onNext(ValidateTradeResponse.newBuilder()
                        .setValid(false)
                        .setRejectCode(RejectCode.CURRENCY_MISMATCH)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            if (request.getTradeType() == TradeType.TRADE_TYPE_BUY) {
                CashAccountEntity cashAccount = cashAccountRepository.findByInvestorIdAndCurrency(investorId, currency)
                        .orElse(null);
                BigDecimal availableFunds = cashAccount != null ? cashAccount.getBalance() : BigDecimal.ZERO;

                if (availableFunds.compareTo(totalAmount) < 0) {
                    responseObserver.onNext(ValidateTradeResponse.newBuilder()
                            .setValid(false)
                            .setRejectCode(RejectCode.INSUFFICIENT_FUNDS)
                            .setAvailableFunds(availableFunds.toPlainString())
                            .build());
                    responseObserver.onCompleted();
                    return;
                }

                responseObserver.onNext(ValidateTradeResponse.newBuilder()
                        .setValid(true)
                        .setRejectCode(RejectCode.REJECT_CODE_NONE)
                        .setAvailableFunds(availableFunds.toPlainString())
                        .build());
                responseObserver.onCompleted();

            } else if (request.getTradeType() == TradeType.TRADE_TYPE_SELL) {
                Optional<PortfolioPositionEntity> posOpt = positionRepository.findByPortfolioIdAndAssetId(portfolioId, assetId);
                BigDecimal availableQty = posOpt.map(PortfolioPositionEntity::getQuantity).orElse(BigDecimal.ZERO);

                if (availableQty.compareTo(quantity) < 0) {
                    responseObserver.onNext(ValidateTradeResponse.newBuilder()
                            .setValid(false)
                            .setRejectCode(RejectCode.INSUFFICIENT_POSITION)
                            .setAvailableQuantity(availableQty.toPlainString())
                            .build());
                    responseObserver.onCompleted();
                    return;
                }

                responseObserver.onNext(ValidateTradeResponse.newBuilder()
                        .setValid(true)
                        .setRejectCode(RejectCode.REJECT_CODE_NONE)
                        .setAvailableQuantity(availableQty.toPlainString())
                        .build());
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Unknown trade type").asRuntimeException());
            }

        } catch (Exception e) {
            log.error("Error in validateTrade", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkFunds(CheckFundsRequest request, StreamObserver<CheckFundsResponse> responseObserver) {
        try {
            UUID investorId = UUID.fromString(request.getInvestorId());
            BigDecimal amount = new BigDecimal(request.getAmount());
            String currency = request.getCurrency();

            CashAccountEntity cashAccount = cashAccountRepository.findByInvestorIdAndCurrency(investorId, currency)
                    .orElse(null);
            BigDecimal available = cashAccount != null ? cashAccount.getBalance() : BigDecimal.ZERO;

            boolean sufficient = available.compareTo(amount) >= 0;

            responseObserver.onNext(CheckFundsResponse.newBuilder()
                    .setSufficient(sufficient)
                    .setAvailableAmount(available.toPlainString())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in checkFunds", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getPortfolioSnapshot(SnapshotRequest request, StreamObserver<SnapshotResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            Optional<PortfolioEntity> portOpt = portfolioRepository.findById(portfolioId);

            if (portOpt.isEmpty()) {
                responseObserver.onNext(SnapshotResponse.newBuilder().setFound(false).build());
                responseObserver.onCompleted();
                return;
            }

            PortfolioEntity portfolio = portOpt.get();
            List<PortfolioPositionEntity> positions = positionRepository.findByPortfolioId(portfolioId);

            BigDecimal totalValue = BigDecimal.ZERO;
            SnapshotResponse.Builder builder = SnapshotResponse.newBuilder()
                    .setFound(true)
                    .setInvestorId(portfolio.getInvestorId().toString())
                    .setBaseCurrency(portfolio.getBaseCurrency())
                    .setCalculatedAt(Instant.now().toString());

            for (PortfolioPositionEntity pos : positions) {
                BigDecimal currentPrice = priceCacheRepository.findById(pos.getAssetId())
                        .map(AssetPriceCacheEntity::getCurrentPrice)
                        .orElse(pos.getAvgPurchasePrice());
                BigDecimal marketValue = pos.getQuantity().multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
                totalValue = totalValue.add(marketValue);

                builder.addPositions(PositionSnapshot.newBuilder()
                        .setAssetId(pos.getAssetId().toString())
                        .setQuantity(pos.getQuantity().toPlainString())
                        .setAvgPurchasePrice(pos.getAvgPurchasePrice().toPlainString())
                        .setCurrentPrice(currentPrice.toPlainString())
                        .setMarketValue(marketValue.toPlainString())
                        .build());
            }

            BigDecimal cashBalance = cashAccountRepository.findByInvestorIdAndCurrency(portfolio.getInvestorId(), portfolio.getBaseCurrency())
                    .map(CashAccountEntity::getBalance)
                    .orElse(BigDecimal.ZERO);

            builder.setTotalValue(totalValue.toPlainString());
            builder.setCashBalance(cashBalance.toPlainString());

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getPortfolioSnapshot", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getAssetHolders(AssetHoldersRequest request, StreamObserver<AssetHoldersResponse> responseObserver) {
        try {
            UUID assetId = UUID.fromString(request.getAssetId());
            List<UUID> investorIds = positionRepository.findDistinctInvestorIdsByAssetId(assetId);

            AssetHoldersResponse.Builder builder = AssetHoldersResponse.newBuilder();
            for (UUID id : investorIds) {
                builder.addInvestorIds(id.toString());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getAssetHolders", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void createPortfolio(CreatePortfolioRequest request, StreamObserver<PortfolioResponse> responseObserver) {
        try {
            UUID investorId = UUID.fromString(request.getInvestorId());
            String name = request.getName().trim();
            String currency = request.getBaseCurrency().toUpperCase().trim();

            if (portfolioRepository.existsByInvestorIdAndName(investorId, name)) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription("Portfolio name already exists").asRuntimeException());
                return;
            }

            PortfolioEntity portfolio = PortfolioEntity.builder()
                    .investorId(investorId)
                    .name(name)
                    .baseCurrency(currency)
                    .lastPublishedValue(BigDecimal.ZERO)
                    .build();

            PortfolioEntity saved = portfolioRepository.save(portfolio);

            // Ensure cash account exists
            cashAccountRepository.findByInvestorIdAndCurrency(investorId, currency)
                    .orElseGet(() -> cashAccountRepository.save(CashAccountEntity.builder()
                            .investorId(investorId)
                            .currency(currency)
                            .balance(BigDecimal.ZERO)
                            .build()));

            responseObserver.onNext(mapToPortfolioResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in createPortfolio", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listPortfolios(ListPortfoliosRequest request, StreamObserver<ListPortfoliosResponse> responseObserver) {
        try {
            UUID investorId = UUID.fromString(request.getInvestorId());
            Sort sort = Sort.by("name").ascending();

            List<PortfolioEntity> list = portfolioRepository.findByInvestorId(investorId, sort);
            List<PortfolioResponse> responses = new ArrayList<>();

            for (PortfolioEntity p : list) {
                responses.add(mapToPortfolioResponse(p));
            }

            // In-memory sort by calculated fields if requested
            if (request.getSortField() == PortfolioSortField.PORTFOLIO_SORT_VALUE) {
                responses.sort((a, b) -> new BigDecimal(a.getTotalValue()).compareTo(new BigDecimal(b.getTotalValue())));
                if (request.getSortDirection() == PortfolioSortDirection.PORTFOLIO_DESC) {
                    Collections.reverse(responses);
                }
            } else if (request.getSortField() == PortfolioSortField.PORTFOLIO_SORT_PROFIT_PERCENT) {
                responses.sort((a, b) -> new BigDecimal(a.getProfitPercent()).compareTo(new BigDecimal(b.getProfitPercent())));
                if (request.getSortDirection() == PortfolioSortDirection.PORTFOLIO_DESC) {
                    Collections.reverse(responses);
                }
            }

            ListPortfoliosResponse response = ListPortfoliosResponse.newBuilder()
                    .addAllPortfolios(responses)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in listPortfolios", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getPortfolio(GetPortfolioRequest request, StreamObserver<PortfolioResponse> responseObserver) {
        try {
            UUID portfolioId = UUID.fromString(request.getPortfolioId());
            PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Portfolio not found: " + portfolioId).asRuntimeException());

            responseObserver.onNext(mapToPortfolioResponse(portfolio));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getPortfolio", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private PortfolioResponse mapToPortfolioResponse(PortfolioEntity portfolio) {
        List<PortfolioPositionEntity> positions = positionRepository.findByPortfolioId(portfolio.getId());

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;

        List<PositionResponse> posResponses = new ArrayList<>();
        for (PortfolioPositionEntity pos : positions) {
            BigDecimal price = priceCacheRepository.findById(pos.getAssetId())
                    .map(AssetPriceCacheEntity::getCurrentPrice)
                    .orElse(pos.getAvgPurchasePrice());

            BigDecimal marketValue = pos.getQuantity().multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal invested = pos.getQuantity().multiply(pos.getAvgPurchasePrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profit = marketValue.subtract(invested);
            BigDecimal profitPercent = BigDecimal.ZERO;
            if (invested.compareTo(BigDecimal.ZERO) > 0) {
                profitPercent = profit.divide(invested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            totalMarketValue = totalMarketValue.add(marketValue);
            totalInvested = totalInvested.add(invested);

            posResponses.add(PositionResponse.newBuilder()
                    .setAssetId(pos.getAssetId().toString())
                    .setQuantity(pos.getQuantity().toPlainString())
                    .setAvgPurchasePrice(pos.getAvgPurchasePrice().toPlainString())
                    .setCurrentPrice(price.toPlainString())
                    .setMarketValue(marketValue.toPlainString())
                    .setProfit(profit.toPlainString())
                    .setProfitPercent(profitPercent.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .build());
        }

        BigDecimal totalProfit = totalMarketValue.subtract(totalInvested);
        BigDecimal totalProfitPercent = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitPercent = totalProfit.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        BigDecimal cashBalance = cashAccountRepository.findByInvestorIdAndCurrency(portfolio.getInvestorId(), portfolio.getBaseCurrency())
                .map(CashAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);

        return PortfolioResponse.newBuilder()
                .setId(portfolio.getId().toString())
                .setInvestorId(portfolio.getInvestorId().toString())
                .setName(portfolio.getName())
                .setBaseCurrency(portfolio.getBaseCurrency())
                .setCreatedAt(portfolio.getCreatedAt() != null ? portfolio.getCreatedAt().toString() : Instant.now().toString())
                .addAllPositions(posResponses)
                .setTotalValue(totalMarketValue.toPlainString())
                .setInvestedAmount(totalInvested.toPlainString())
                .setProfit(totalProfit.toPlainString())
                .setProfitPercent(totalProfitPercent.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .setCashBalance(cashBalance.toPlainString())
                .build();
    }
}
