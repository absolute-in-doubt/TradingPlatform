package absolute_in_doubt.trading_platform.getaway.mapper;

import absolute_in_doubt.trading_platform.getaway.dto.*;
import invest.analytics.v1.CategoryShareResponse;
import invest.analytics.v1.PlatformStatsResponse;
import invest.analytics.v1.PortfolioPerformanceResponse;
import invest.analytics.v1.ReportResponse;
import invest.asset.v1.AssetResponse;
import invest.asset.v1.CategoryResponse;
import invest.notification.v1.NotificationResponse;
import invest.payment.v1.PaymentResponse;
import invest.portfolio.v1.PortfolioResponse;
import invest.portfolio.v1.PositionResponse;
import invest.user.v1.AuthResponse;
import invest.user.v1.BrokerProfileResponse;
import invest.user.v1.InvestorProfileResponse;
import invest.user.v1.RiskProfile;
import invest.user.v1.UserResponse;
import invest.user.v1.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ProtoMapper {

    private ProtoMapper() {}

    public static Instant parseInstant(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Instant.parse(val);
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate parseLocalDate(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return LocalDate.parse(val);
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public static AuthDtos.UserDto toUserDto(UserResponse res) {
        if (res == null || res.getId().isBlank()) return null;

        AuthDtos.InvestorProfileDto investorProfile = null;
        if (res.hasInvestorProfile() && res.getInvestorProfile().getRiskProfile() != RiskProfile.RISK_PROFILE_UNSPECIFIED) {
            InvestorProfileResponse ip = res.getInvestorProfile();
            investorProfile = AuthDtos.InvestorProfileDto.builder()
                    .riskProfile(ip.getRiskProfile().name())
                    .investingSince(parseLocalDate(ip.getInvestingSince()))
                    .broker(ip.hasBroker() ? toUserDto(ip.getBroker()) : null)
                    .build();
        }

        AuthDtos.BrokerProfileDto brokerProfile = null;
        if (res.hasBrokerProfile() && !res.getBrokerProfile().getLicenseNumber().isBlank()) {
            BrokerProfileResponse bp = res.getBrokerProfile();
            brokerProfile = AuthDtos.BrokerProfileDto.builder()
                    .licenseNumber(bp.getLicenseNumber())
                    .specialization(bp.getSpecialization())
                    .build();
        }

        return AuthDtos.UserDto.builder()
                .id(res.getId())
                .email(res.getEmail())
                .fullName(res.getFullName())
                .role(res.getRole().name())
                .status(res.getStatus() == UserStatus.USER_STATUS_BLOCKED ? "BLOCKED" : "ACTIVE")
                .registeredAt(parseInstant(res.getRegisteredAt()))
                .investorProfile(investorProfile)
                .brokerProfile(brokerProfile)
                .build();
    }

    public static AuthDtos.AuthPayloadDto toAuthPayloadDto(AuthResponse res) {
        if (res == null) return null;
        return AuthDtos.AuthPayloadDto.builder()
                .accessToken(res.getAccessToken())
                .expiresAt(parseInstant(res.getExpiresAt()))
                .user(toUserDto(res.getUser()))
                .build();
    }

    public static AssetDtos.AssetCategoryDto toAssetCategoryDto(CategoryResponse res) {
        if (res == null) return null;
        return AssetDtos.AssetCategoryDto.builder()
                .id(res.getId())
                .name(res.getName())
                .description(res.getDescription())
                .build();
    }

    public static AssetDtos.AssetDto toAssetDto(AssetResponse res) {
        if (res == null) return null;
        return AssetDtos.AssetDto.builder()
                .id(res.getId())
                .ticker(res.getTicker())
                .name(res.getName())
                .category(res.hasCategory() ? toAssetCategoryDto(res.getCategory()) : null)
                .currentPrice(parseBigDecimal(res.getCurrentPrice()))
                .currency(res.getCurrency())
                .isActive(res.getIsActive())
                .priceUpdatedAt(parseInstant(res.getPriceUpdatedAt()))
                .build();
    }

    public static PortfolioDtos.PositionDto toPositionDto(PositionResponse res) {
        if (res == null) return null;
        return PortfolioDtos.PositionDto.builder()
                .assetId(res.getAssetId())
                .quantity(parseBigDecimal(res.getQuantity()))
                .avgPurchasePrice(parseBigDecimal(res.getAvgPurchasePrice()))
                .currentPrice(parseBigDecimal(res.getCurrentPrice()))
                .marketValue(parseBigDecimal(res.getMarketValue()))
                .profit(parseBigDecimal(res.getProfit()))
                .profitPercent(parseBigDecimal(res.getProfitPercent()))
                .build();
    }

    public static PortfolioDtos.PortfolioDto toPortfolioDto(PortfolioResponse res) {
        if (res == null) return null;
        return PortfolioDtos.PortfolioDto.builder()
                .id(res.getId())
                .investorId(res.getInvestorId())
                .name(res.getName())
                .baseCurrency(res.getBaseCurrency())
                .createdAt(parseInstant(res.getCreatedAt()))
                .positions(res.getPositionsList().stream().map(ProtoMapper::toPositionDto).toList())
                .totalValue(parseBigDecimal(res.getTotalValue()))
                .investedAmount(parseBigDecimal(res.getInvestedAmount()))
                .profit(parseBigDecimal(res.getProfit()))
                .profitPercent(parseBigDecimal(res.getProfitPercent()))
                .cashBalance(parseBigDecimal(res.getCashBalance()))
                .build();
    }

    public static TradingDtos.TradeDto toTradeDto(invest.trading.v1.TradeResponse res) {
        if (res == null) return null;
        return TradingDtos.TradeDto.builder()
                .id(res.getId())
                .portfolioId(res.getPortfolioId())
                .investorId(res.getInvestorId())
                .brokerId(res.getBrokerId())
                .assetId(res.getAssetId())
                .type(res.getTradeType().name())
                .quantity(parseBigDecimal(res.getQuantity()))
                .price(parseBigDecimal(res.getPrice()))
                .totalAmount(parseBigDecimal(res.getTotalAmount()))
                .currency(res.getCurrency())
                .status(res.getStatus().name())
                .rejectReason(res.getRejectReason().isBlank() ? null : res.getRejectReason())
                .createdAt(parseInstant(res.getExecutedAt()))
                .build();
    }

    public static PaymentDtos.PaymentOperationDto toPaymentDto(PaymentResponse res) {
        if (res == null) return null;
        return PaymentDtos.PaymentOperationDto.builder()
                .id(res.getId())
                .type(res.getType().name())
                .amount(parseBigDecimal(res.getAmount()))
                .currency(res.getCurrency())
                .status(res.getStatus().name())
                .rejectReason(res.getRejectReason().isBlank() ? null : res.getRejectReason())
                .createdAt(parseInstant(res.getProcessedAt()))
                .build();
    }

    public static AnalyticsDtos.CategoryShareDto toCategoryShareDto(CategoryShareResponse res) {
        if (res == null) return null;
        return AnalyticsDtos.CategoryShareDto.builder()
                .category(res.getCategory())
                .value(parseBigDecimal(res.getValue()))
                .share(parseBigDecimal(res.getShare()))
                .build();
    }

    public static AnalyticsDtos.PortfolioPerformanceDto toPortfolioPerformanceDto(PortfolioPerformanceResponse res) {
        if (res == null) return null;
        return AnalyticsDtos.PortfolioPerformanceDto.builder()
                .portfolioId(res.getPortfolioId())
                .periodStart(parseLocalDate(res.getPeriodStart()))
                .periodEnd(parseLocalDate(res.getPeriodEnd()))
                .currency(res.getCurrency())
                .startValue(parseBigDecimal(res.getStartValue()))
                .endValue(parseBigDecimal(res.getEndValue()))
                .purchasesAmount(parseBigDecimal(res.getPurchasesAmount()))
                .salesAmount(parseBigDecimal(res.getSalesAmount()))
                .profit(parseBigDecimal(res.getProfit()))
                .yieldPercent(parseBigDecimal(res.getYieldPercent()))
                .build();
    }

    public static AnalyticsDtos.ReportDto toReportDto(ReportResponse res) {
        if (res == null) return null;
        return AnalyticsDtos.ReportDto.builder()
                .id(res.getId())
                .portfolioId(res.getPortfolioId())
                .type(res.getType().name())
                .periodStart(parseLocalDate(res.getPeriodStart()))
                .periodEnd(parseLocalDate(res.getPeriodEnd()))
                .currency(res.getCurrency())
                .startValue(parseBigDecimal(res.getStartValue()))
                .endValue(parseBigDecimal(res.getEndValue()))
                .purchasesAmount(parseBigDecimal(res.getPurchasesAmount()))
                .salesAmount(parseBigDecimal(res.getSalesAmount()))
                .netCashFlow(parseBigDecimal(res.getNetCashFlow()))
                .profit(parseBigDecimal(res.getProfit()))
                .yieldPercent(parseBigDecimal(res.getYieldPercent()))
                .structure(res.getStructureList().stream().map(ProtoMapper::toCategoryShareDto).toList())
                .generatedAt(parseInstant(res.getGeneratedAt()))
                .build();
    }

    public static AnalyticsDtos.PlatformStatsDto toPlatformStatsDto(PlatformStatsResponse res) {
        if (res == null) return null;
        return AnalyticsDtos.PlatformStatsDto.builder()
                .periodStart(parseLocalDate(res.getPeriodStart()))
                .periodEnd(parseLocalDate(res.getPeriodEnd()))
                .investorsCount(res.getInvestorsCount())
                .portfoliosCount(res.getPortfoliosCount())
                .tradesCount(res.getTradesCount())
                .tradesVolume(parseBigDecimal(res.getTradesVolume()))
                .depositsVolume(parseBigDecimal(res.getDepositsVolume()))
                .withdrawalsVolume(parseBigDecimal(res.getWithdrawalsVolume()))
                .totalPortfolioValue(parseBigDecimal(res.getTotalPortfolioValue()))
                .structure(res.getStructureList().stream().map(ProtoMapper::toCategoryShareDto).toList())
                .build();
    }

    public static NotificationDtos.NotificationDto toNotificationDto(NotificationResponse res) {
        if (res == null) return null;
        return NotificationDtos.NotificationDto.builder()
                .id(res.getId())
                .type(res.getType().name())
                .message(res.getMessage())
                .deliveryStatus(res.getDeliveryStatus().name())
                .isRead(res.getIsRead())
                .readAt(parseInstant(res.getReadAt()))
                .createdAt(parseInstant(res.getCreatedAt()))
                .build();
    }
}
