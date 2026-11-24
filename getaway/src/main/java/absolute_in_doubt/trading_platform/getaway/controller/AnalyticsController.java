package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.AnalyticsDtos;
import absolute_in_doubt.trading_platform.getaway.dto.CommonDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import invest.analytics.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsServiceGrpc.AnalyticsServiceBlockingStub analyticsServiceStub;

    @QueryMapping
    public AnalyticsDtos.PortfolioPerformanceDto portfolioPerformance(
            @Argument String portfolioId,
            @Argument LocalDate periodStart,
            @Argument LocalDate periodEnd
    ) {
        GetPerformanceRequest request = GetPerformanceRequest.newBuilder()
                .setPortfolioId(portfolioId)
                .setPeriodStart(periodStart.toString())
                .setPeriodEnd(periodEnd.toString())
                .build();

        PortfolioPerformanceResponse response = analyticsServiceStub.getPerformance(request);
        return ProtoMapper.toPortfolioPerformanceDto(response);
    }

    @QueryMapping
    public AnalyticsDtos.ReportPageDto reports(
            @Argument String portfolioId,
            @Argument CommonDtos.PageInput page
    ) {
        int limit = page != null ? page.getEffectiveLimit() : 20;
        int offset = page != null ? page.getEffectiveOffset() : 0;

        ListReportsRequest request = ListReportsRequest.newBuilder()
                .setPortfolioId(portfolioId)
                .setLimit(limit)
                .setOffset(offset)
                .build();

        ListReportsResponse response = analyticsServiceStub.listReports(request);
        List<AnalyticsDtos.ReportDto> items = response.getItemsList().stream()
                .map(ProtoMapper::toReportDto)
                .toList();

        return AnalyticsDtos.ReportPageDto.builder()
                .items(items)
                .totalCount(response.getTotalCount())
                .build();
    }

    @QueryMapping
    public AnalyticsDtos.ReportDto report(@Argument String id) {
        try {
            GetReportRequest request = GetReportRequest.newBuilder()
                    .setReportId(id)
                    .build();

            ReportResponse response = analyticsServiceStub.getReport(request);
            return ProtoMapper.toReportDto(response);
        } catch (Exception e) {
            return null;
        }
    }

    @QueryMapping
    public AnalyticsDtos.PlatformStatsDto platformStats(
            @Argument LocalDate periodStart,
            @Argument LocalDate periodEnd
    ) {
        GetPlatformStatsRequest request = GetPlatformStatsRequest.newBuilder()
                .setPeriodStart(periodStart.toString())
                .setPeriodEnd(periodEnd.toString())
                .build();

        PlatformStatsResponse response = analyticsServiceStub.getPlatformStats(request);
        return ProtoMapper.toPlatformStatsDto(response);
    }

    @MutationMapping
    public AnalyticsDtos.ReportDto generateReport(@Argument AnalyticsDtos.GenerateReportInput input) {
        GenerateReportRequest request = GenerateReportRequest.newBuilder()
                .setPortfolioId(input.portfolioId())
                .setPeriodStart(input.periodStart().toString())
                .setPeriodEnd(input.periodEnd().toString())
                .setType(ReportType.ON_DEMAND)
                .build();

        ReportResponse response = analyticsServiceStub.generateReport(request);
        return ProtoMapper.toReportDto(response);
    }
}
