package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.AssetDtos;
import absolute_in_doubt.trading_platform.getaway.dto.PortfolioDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import absolute_in_doubt.trading_platform.getaway.security.SecurityUtils;
import absolute_in_doubt.trading_platform.getaway.security.UserPrincipal;
import invest.asset.v1.AssetServiceGrpc;
import invest.asset.v1.GetAssetsByIdsRequest;
import invest.asset.v1.GetAssetsByIdsResponse;
import invest.portfolio.v1.*;
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
public class PortfolioController {

    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub;

    @QueryMapping
    public List<PortfolioDtos.PortfolioDto> myPortfolios(
            @Argument(name = "sort") PortfolioDtos.PortfolioSort sort,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        ListPortfoliosRequest.Builder builder = ListPortfoliosRequest.newBuilder()
                .setInvestorId(currentUser.getUserId().toString());

        if (sort != null) {
            if ("VALUE".equalsIgnoreCase(sort.field())) {
                builder.setSortField(PortfolioSortField.PORTFOLIO_SORT_VALUE);
            } else if ("PROFIT_PERCENT".equalsIgnoreCase(sort.field())) {
                builder.setSortField(PortfolioSortField.PORTFOLIO_SORT_PROFIT_PERCENT);
            } else {
                builder.setSortField(PortfolioSortField.PORTFOLIO_SORT_NAME);
            }

            if ("DESC".equalsIgnoreCase(sort.direction())) {
                builder.setSortDirection(PortfolioSortDirection.PORTFOLIO_DESC);
            } else {
                builder.setSortDirection(PortfolioSortDirection.PORTFOLIO_ASC);
            }
        }

        ListPortfoliosResponse response = portfolioServiceStub.listPortfolios(builder.build());
        List<PortfolioDtos.PortfolioDto> dtos = response.getPortfoliosList().stream()
                .map(ProtoMapper::toPortfolioDto)
                .toList();

        enrichPositionsWithAssets(dtos);
        return dtos;
    }

    @QueryMapping
    public PortfolioDtos.PortfolioDto portfolio(@Argument String id) {
        try {
            PortfolioResponse response = portfolioServiceStub.getPortfolio(
                    GetPortfolioRequest.newBuilder().setPortfolioId(id).build()
            );
            PortfolioDtos.PortfolioDto dto = ProtoMapper.toPortfolioDto(response);
            enrichPositionsWithAssets(Collections.singletonList(dto));
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    @MutationMapping
    public PortfolioDtos.PortfolioDto createPortfolio(
            @Argument PortfolioDtos.CreatePortfolioInput input,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        CreatePortfolioRequest request = CreatePortfolioRequest.newBuilder()
                .setInvestorId(currentUser.getUserId().toString())
                .setName(input.name())
                .setBaseCurrency(input.baseCurrency())
                .build();

        PortfolioResponse response = portfolioServiceStub.createPortfolio(request);
        PortfolioDtos.PortfolioDto dto = ProtoMapper.toPortfolioDto(response);
        enrichPositionsWithAssets(Collections.singletonList(dto));
        return dto;
    }

    private void enrichPositionsWithAssets(List<PortfolioDtos.PortfolioDto> portfolios) {
        Set<String> assetIds = portfolios.stream()
                .filter(p -> p.getPositions() != null)
                .flatMap(p -> p.getPositions().stream())
                .map(PortfolioDtos.PositionDto::getAssetId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (assetIds.isEmpty()) return;

        GetAssetsByIdsResponse assetRes = assetServiceStub.getAssetsByIds(
                GetAssetsByIdsRequest.newBuilder().addAllAssetIds(assetIds).build()
        );

        Map<String, AssetDtos.AssetDto> assetMap = assetRes.getItemsList().stream()
                .map(ProtoMapper::toAssetDto)
                .collect(Collectors.toMap(AssetDtos.AssetDto::getId, Function.identity(), (a, b) -> a));

        for (PortfolioDtos.PortfolioDto p : portfolios) {
            if (p.getPositions() != null) {
                for (PortfolioDtos.PositionDto pos : p.getPositions()) {
                    pos.setAsset(assetMap.get(pos.getAssetId()));
                }
            }
        }
    }
}
