package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.*;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import absolute_in_doubt.trading_platform.getaway.security.SecurityUtils;
import absolute_in_doubt.trading_platform.getaway.security.UserPrincipal;
import invest.asset.v1.AssetServiceGrpc;
import invest.asset.v1.GetAssetsByIdsRequest;
import invest.asset.v1.GetAssetsByIdsResponse;
import invest.portfolio.v1.GetPortfolioRequest;
import invest.portfolio.v1.PortfolioResponse;
import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.trading.v1.*;
import invest.user.v1.GetUserRequest;
import invest.user.v1.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class TradingController {

    private final TradingServiceGrpc.TradingServiceBlockingStub tradingServiceStub;
    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub;
    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @QueryMapping
    public TradingDtos.TradePageDto trades(
            @Argument(name = "filter") TradingDtos.TradeFilter filter,
            @Argument(name = "sort") TradingDtos.TradeSort sort,
            @Argument(name = "page") CommonDtos.PageInput page,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        ListTradesRequest.Builder builder = ListTradesRequest.newBuilder();

        if (filter != null) {
            if (filter.portfolioId() != null) builder.setPortfolioId(filter.portfolioId());
            if (filter.investorId() != null) builder.setInvestorId(filter.investorId());
            if (filter.assetId() != null) builder.setAssetId(filter.assetId());
            if (filter.type() != null) {
                builder.setTradeType(TradeType.valueOf(filter.type().toUpperCase()));
            }
            if (filter.status() != null) {
                builder.setStatus(TradeStatus.valueOf(filter.status().toUpperCase()));
            }
            if (filter.dateFrom() != null) builder.setDateFrom(filter.dateFrom().toString());
            if (filter.dateTo() != null) builder.setDateTo(filter.dateTo().toString());
        }

        // If user is INVESTOR and investorId not filtered, restrict to currentUser
        if ("INVESTOR".equalsIgnoreCase(currentUser.getRole()) && builder.getInvestorId().isBlank()) {
            builder.setInvestorId(currentUser.getUserId().toString());
        }

        if (sort != null) {
            if ("AMOUNT".equalsIgnoreCase(sort.field())) {
                builder.setSortField(TradeSortField.TRADE_SORT_AMOUNT);
            } else {
                builder.setSortField(TradeSortField.TRADE_SORT_DATE);
            }

            if ("ASC".equalsIgnoreCase(sort.direction())) {
                builder.setSortDirection(SortDirection.SORT_ASC);
            } else {
                builder.setSortDirection(SortDirection.SORT_DESC);
            }
        }

        int limit = page != null ? page.getEffectiveLimit() : 20;
        int offset = page != null ? page.getEffectiveOffset() : 0;
        builder.setLimit(limit).setOffset(offset);

        ListTradesResponse response = tradingServiceStub.listTrades(builder.build());
        List<TradingDtos.TradeDto> items = response.getItemsList().stream()
                .map(ProtoMapper::toTradeDto)
                .toList();

        enrichTrades(items);

        return TradingDtos.TradePageDto.builder()
                .items(items)
                .totalCount(response.getTotalCount())
                .build();
    }

    @MutationMapping
    public TradingDtos.TradeDto buyAsset(
            @Argument TradingDtos.TradeInput input,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        return executeTrade(input, TradeType.BUY, currentUser);
    }

    @MutationMapping
    public TradingDtos.TradeDto sellAsset(
            @Argument TradingDtos.TradeInput input,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        return executeTrade(input, TradeType.SELL, currentUser);
    }

    private TradingDtos.TradeDto executeTrade(
            TradingDtos.TradeInput input,
            TradeType tradeType,
            UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        // Fetch portfolio to obtain investorId and verify ownership
        PortfolioResponse portfolio = portfolioServiceStub.getPortfolio(
                GetPortfolioRequest.newBuilder().setPortfolioId(input.portfolioId()).build()
        );

        String investorId = portfolio.getInvestorId();
        String brokerId = "";

        if ("INVESTOR".equalsIgnoreCase(currentUser.getRole())) {
            if (!currentUser.getUserId().toString().equals(investorId)) {
                throw new RuntimeException("Portfolio does not belong to the current investor");
            }
        } else if ("BROKER".equalsIgnoreCase(currentUser.getRole())) {
            brokerId = currentUser.getUserId().toString();
        }

        RegisterTradeRequest request = RegisterTradeRequest.newBuilder()
                .setPortfolioId(input.portfolioId())
                .setInvestorId(investorId)
                .setBrokerId(brokerId)
                .setAssetId(input.assetId())
                .setTradeType(tradeType)
                .setQuantity(input.quantity().toPlainString())
                .build();

        TradeResponse response = tradingServiceStub.registerTrade(request);
        TradingDtos.TradeDto dto = ProtoMapper.toTradeDto(response);
        enrichTrades(Collections.singletonList(dto));
        return dto;
    }

    private void enrichTrades(List<TradingDtos.TradeDto> trades) {
        if (trades.isEmpty()) return;

        // 1. Assets
        Set<String> assetIds = trades.stream()
                .map(TradingDtos.TradeDto::getAssetId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (!assetIds.isEmpty()) {
            GetAssetsByIdsResponse assetRes = assetServiceStub.getAssetsByIds(
                    GetAssetsByIdsRequest.newBuilder().addAllAssetIds(assetIds).build()
            );
            Map<String, AssetDtos.AssetDto> assetMap = assetRes.getItemsList().stream()
                    .map(ProtoMapper::toAssetDto)
                    .collect(Collectors.toMap(AssetDtos.AssetDto::getId, Function.identity(), (a, b) -> a));

            for (TradingDtos.TradeDto t : trades) {
                t.setAsset(assetMap.get(t.getAssetId()));
            }
        }

        // 2. Portfolios
        Map<String, PortfolioDtos.PortfolioDto> portfolioMap = new HashMap<>();
        for (TradingDtos.TradeDto t : trades) {
            if (t.getPortfolioId() != null && !t.getPortfolioId().isBlank()) {
                portfolioMap.computeIfAbsent(t.getPortfolioId(), pid -> {
                    try {
                        PortfolioResponse pr = portfolioServiceStub.getPortfolio(
                                GetPortfolioRequest.newBuilder().setPortfolioId(pid).build()
                        );
                        return ProtoMapper.toPortfolioDto(pr);
                    } catch (Exception e) {
                        return null;
                    }
                });
                t.setPortfolio(portfolioMap.get(t.getPortfolioId()));
            }
        }

        // 3. Brokers
        Map<String, AuthDtos.UserDto> brokerMap = new HashMap<>();
        for (TradingDtos.TradeDto t : trades) {
            if (t.getBrokerId() != null && !t.getBrokerId().isBlank()) {
                brokerMap.computeIfAbsent(t.getBrokerId(), bid -> {
                    try {
                        return ProtoMapper.toUserDto(
                                userServiceStub.getUser(GetUserRequest.newBuilder().setUserId(bid).build())
                        );
                    } catch (Exception e) {
                        return null;
                    }
                });
                t.setBroker(brokerMap.get(t.getBrokerId()));
            }
        }
    }
}
