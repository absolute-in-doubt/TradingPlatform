package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.AssetDtos;
import absolute_in_doubt.trading_platform.getaway.dto.CommonDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import invest.asset.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AssetController {

    private final AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub;

    @QueryMapping
    public List<AssetDtos.AssetCategoryDto> assetCategories() {
        ListCategoriesResponse response = assetServiceStub.listCategories(ListCategoriesRequest.getDefaultInstance());
        return response.getCategoriesList().stream()
                .map(ProtoMapper::toAssetCategoryDto)
                .toList();
    }

    @QueryMapping
    public AssetDtos.AssetPageDto assets(
            @Argument(name = "filter") AssetDtos.AssetFilter filter,
            @Argument(name = "sort") AssetDtos.AssetSort sort,
            @Argument(name = "page") CommonDtos.PageInput page
    ) {
        ListAssetsRequest.Builder builder = ListAssetsRequest.newBuilder();

        if (filter != null) {
            if (filter.categoryId() != null) builder.setCategoryId(filter.categoryId());
            if (filter.minPrice() != null) builder.setMinPrice(filter.minPrice().toPlainString());
            if (filter.maxPrice() != null) builder.setMaxPrice(filter.maxPrice().toPlainString());
            if (filter.search() != null) builder.setSearch(filter.search());
            builder.setOnlyActive(filter.onlyActive() == null || filter.onlyActive());
        } else {
            builder.setOnlyActive(true);
        }

        if (sort != null) {
            if ("NAME".equalsIgnoreCase(sort.field())) {
                builder.setSortField(AssetSortField.SORT_NAME);
            } else if ("PRICE".equalsIgnoreCase(sort.field())) {
                builder.setSortField(AssetSortField.SORT_PRICE);
            } else {
                builder.setSortField(AssetSortField.SORT_TICKER);
            }

            if ("DESC".equalsIgnoreCase(sort.direction())) {
                builder.setSortDirection(SortDirection.SORT_DESC);
            } else {
                builder.setSortDirection(SortDirection.SORT_ASC);
            }
        }

        int limit = page != null ? page.getEffectiveLimit() : 20;
        int offset = page != null ? page.getEffectiveOffset() : 0;
        builder.setLimit(limit).setOffset(offset);

        ListAssetsResponse response = assetServiceStub.listAssets(builder.build());
        List<AssetDtos.AssetDto> items = response.getItemsList().stream()
                .map(ProtoMapper::toAssetDto)
                .toList();

        return AssetDtos.AssetPageDto.builder()
                .items(items)
                .totalCount(response.getTotalCount())
                .build();
    }

    @QueryMapping
    public AssetDtos.AssetDto asset(@Argument String id) {
        GetAssetsByIdsResponse response = assetServiceStub.getAssetsByIds(
                GetAssetsByIdsRequest.newBuilder().addAllAssetIds(Collections.singletonList(id)).build()
        );
        if (response.getItemsCount() == 0) {
            return null;
        }
        return ProtoMapper.toAssetDto(response.getItems(0));
    }

    @MutationMapping
    public AssetDtos.AssetDto createAsset(@Argument AssetDtos.CreateAssetInput input) {
        CreateAssetRequest request = CreateAssetRequest.newBuilder()
                .setTicker(input.ticker())
                .setName(input.name())
                .setCategoryId(input.categoryId())
                .setInitialPrice(input.initialPrice().toPlainString())
                .setCurrency(input.currency())
                .build();

        AssetResponse response = assetServiceStub.createAsset(request);
        return ProtoMapper.toAssetDto(response);
    }

    @MutationMapping
    public AssetDtos.AssetDto updateAssetPrice(@Argument AssetDtos.UpdateAssetPriceInput input) {
        UpdateAssetPriceRequest request = UpdateAssetPriceRequest.newBuilder()
                .setAssetId(input.assetId())
                .setNewPrice(input.newPrice().toPlainString())
                .build();

        AssetResponse response = assetServiceStub.updateAssetPrice(request);
        return ProtoMapper.toAssetDto(response);
    }
}
