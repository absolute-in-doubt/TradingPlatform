package absolute_in_doubt.trading_platform.asset_service.grpc;

import absolute_in_doubt.trading_platform.asset_service.entity.AssetCategoryEntity;
import absolute_in_doubt.trading_platform.asset_service.entity.AssetEntity;
import absolute_in_doubt.trading_platform.asset_service.event.AssetPriceUpdatedPayload;
import absolute_in_doubt.trading_platform.asset_service.event.EventEnvelope;
import absolute_in_doubt.trading_platform.asset_service.repository.AssetCategoryRepository;
import absolute_in_doubt.trading_platform.asset_service.repository.AssetRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.asset.v1.*;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AssetGrpcService extends AssetServiceGrpc.AssetServiceImplBase {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.asset-price-updated:asset.price-updated}")
    private String priceUpdatedTopic;

    @Override
    @Transactional(readOnly = true)
    public void getAsset(GetAssetRequest request, StreamObserver<GetAssetResponse> responseObserver) {
        try {
            UUID assetId = UUID.fromString(request.getAssetId());
            Optional<AssetEntity> assetOpt = assetRepository.findById(assetId);

            if (assetOpt.isEmpty()) {
                responseObserver.onNext(GetAssetResponse.newBuilder().setFound(false).build());
                responseObserver.onCompleted();
                return;
            }

            AssetEntity asset = assetOpt.get();
            GetAssetResponse response = GetAssetResponse.newBuilder()
                    .setFound(true)
                    .setAssetId(asset.getId().toString())
                    .setTicker(asset.getTicker())
                    .setName(asset.getName())
                    .setCurrentPrice(asset.getCurrentPrice().toPlainString())
                    .setCurrency(asset.getCurrency())
                    .setIsActive(Boolean.TRUE.equals(asset.getIsActive()))
                    .setCategoryId(asset.getCategory().getId().toString())
                    .setCategoryName(asset.getCategory().getName())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getAsset", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void createAsset(CreateAssetRequest request, StreamObserver<AssetResponse> responseObserver) {
        try {
            if (assetRepository.existsByTicker(request.getTicker().toUpperCase())) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription("Ticker already exists: " + request.getTicker()).asRuntimeException());
                return;
            }

            Short categoryId = Short.parseShort(request.getCategoryId());
            AssetCategoryEntity category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> Status.INVALID_ARGUMENT.withDescription("Category not found: " + categoryId).asRuntimeException());

            BigDecimal initialPrice = new BigDecimal(request.getInitialPrice());

            AssetEntity asset = AssetEntity.builder()
                    .ticker(request.getTicker().toUpperCase())
                    .name(request.getName())
                    .category(category)
                    .currentPrice(initialPrice)
                    .currency(request.getCurrency().toUpperCase())
                    .isActive(true)
                    .build();

            AssetEntity savedAsset = assetRepository.save(asset);

            // Publish initial AssetPriceUpdated event with oldPrice = null
            AssetPriceUpdatedPayload payload = AssetPriceUpdatedPayload.builder()
                    .assetId(savedAsset.getId().toString())
                    .ticker(savedAsset.getTicker())
                    .categoryName(category.getName())
                    .currency(savedAsset.getCurrency())
                    .oldPrice(null)
                    .newPrice(savedAsset.getCurrentPrice().toPlainString())
                    .changePercent(null)
                    .updatedAt(Instant.now().toString())
                    .build();

            EventEnvelope<AssetPriceUpdatedPayload> envelope = EventEnvelope.<AssetPriceUpdatedPayload>builder()
                    .eventType("AssetPriceUpdated")
                    .producer("asset-service")
                    .payload(payload)
                    .build();

            kafkaTemplate.send(priceUpdatedTopic, savedAsset.getId().toString(), envelope);

            responseObserver.onNext(mapToAssetResponse(savedAsset));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in createAsset", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void updateAssetPrice(UpdateAssetPriceRequest request, StreamObserver<AssetResponse> responseObserver) {
        try {
            UUID assetId = UUID.fromString(request.getAssetId());
            AssetEntity asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Asset not found: " + assetId).asRuntimeException());

            if (!Boolean.TRUE.equals(asset.getIsActive())) {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription("Asset is not active").asRuntimeException());
                return;
            }

            BigDecimal oldPrice = asset.getCurrentPrice();
            BigDecimal newPrice = new BigDecimal(request.getNewPrice());

            BigDecimal changePercent = BigDecimal.ZERO;
            if (oldPrice != null && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
                changePercent = newPrice.subtract(oldPrice)
                        .divide(oldPrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
            }

            asset.setCurrentPrice(newPrice);
            AssetEntity updatedAsset = assetRepository.save(asset);

            AssetPriceUpdatedPayload payload = AssetPriceUpdatedPayload.builder()
                    .assetId(updatedAsset.getId().toString())
                    .ticker(updatedAsset.getTicker())
                    .categoryName(updatedAsset.getCategory().getName())
                    .currency(updatedAsset.getCurrency())
                    .oldPrice(oldPrice != null ? oldPrice.toPlainString() : null)
                    .newPrice(newPrice.toPlainString())
                    .changePercent(changePercent.toPlainString())
                    .updatedAt(Instant.now().toString())
                    .build();

            EventEnvelope<AssetPriceUpdatedPayload> envelope = EventEnvelope.<AssetPriceUpdatedPayload>builder()
                    .eventType("AssetPriceUpdated")
                    .producer("asset-service")
                    .payload(payload)
                    .build();

            kafkaTemplate.send(priceUpdatedTopic, updatedAsset.getId().toString(), envelope);

            responseObserver.onNext(mapToAssetResponse(updatedAsset));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in updateAssetPrice", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listAssets(ListAssetsRequest request, StreamObserver<ListAssetsResponse> responseObserver) {
        try {
            Specification<AssetEntity> spec = (root, query, cb) -> cb.conjunction();

            if (!request.getCategoryId().isBlank()) {
                Short catId = Short.parseShort(request.getCategoryId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), catId));
            }

            if (!request.getMinPrice().isBlank()) {
                BigDecimal min = new BigDecimal(request.getMinPrice());
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("currentPrice"), min));
            }

            if (!request.getMaxPrice().isBlank()) {
                BigDecimal max = new BigDecimal(request.getMaxPrice());
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("currentPrice"), max));
            }

            if (!request.getSearch().isBlank()) {
                String searchLike = "%" + request.getSearch().toLowerCase() + "%";
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.like(cb.lower(root.get("ticker")), searchLike),
                        cb.like(cb.lower(root.get("name")), searchLike)
                ));
            }

            if (request.getOnlyActive()) {
                spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
            }

            String sortProp = "ticker";
            if (request.getSortField() == AssetSortField.SORT_NAME) {
                sortProp = "name";
            } else if (request.getSortField() == AssetSortField.SORT_PRICE) {
                sortProp = "currentPrice";
            }

            Sort sort = request.getSortDirection() == SortDirection.SORT_DESC
                    ? Sort.by(sortProp).descending()
                    : Sort.by(sortProp).ascending();

            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            int offset = Math.max(0, request.getOffset());
            int pageNumber = offset / limit;

            Page<AssetEntity> page = assetRepository.findAll(spec, PageRequest.of(pageNumber, limit, sort));

            ListAssetsResponse.Builder builder = ListAssetsResponse.newBuilder()
                    .setTotalCount((int) page.getTotalElements());

            for (AssetEntity asset : page.getContent()) {
                builder.addItems(mapToAssetResponse(asset));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in listAssets", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getAssetsByIds(GetAssetsByIdsRequest request, StreamObserver<GetAssetsByIdsResponse> responseObserver) {
        try {
            List<UUID> ids = request.getAssetIdsList().stream().map(UUID::fromString).toList();
            List<AssetEntity> assets = assetRepository.findAllById(ids);

            GetAssetsByIdsResponse.Builder builder = GetAssetsByIdsResponse.newBuilder();
            for (AssetEntity asset : assets) {
                builder.addItems(mapToAssetResponse(asset));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getAssetsByIds", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listCategories(ListCategoriesRequest request, StreamObserver<ListCategoriesResponse> responseObserver) {
        try {
            List<AssetCategoryEntity> categories = categoryRepository.findAll();
            ListCategoriesResponse.Builder builder = ListCategoriesResponse.newBuilder();

            for (AssetCategoryEntity cat : categories) {
                builder.addCategories(CategoryResponse.newBuilder()
                        .setId(cat.getId().toString())
                        .setCode(cat.getCode())
                        .setName(cat.getName())
                        .setDescription(cat.getDescription() != null ? cat.getDescription() : "")
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in listCategories", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private AssetResponse mapToAssetResponse(AssetEntity asset) {
        CategoryResponse categoryResponse = CategoryResponse.newBuilder()
                .setId(asset.getCategory().getId().toString())
                .setCode(asset.getCategory().getCode())
                .setName(asset.getCategory().getName())
                .setDescription(asset.getCategory().getDescription() != null ? asset.getCategory().getDescription() : "")
                .build();

        return AssetResponse.newBuilder()
                .setId(asset.getId().toString())
                .setTicker(asset.getTicker())
                .setName(asset.getName())
                .setCategory(categoryResponse)
                .setCurrentPrice(asset.getCurrentPrice().toPlainString())
                .setCurrency(asset.getCurrency())
                .setIsActive(Boolean.TRUE.equals(asset.getIsActive()))
                .setPriceUpdatedAt(asset.getUpdatedAt() != null ? asset.getUpdatedAt().toString() : Instant.now().toString())
                .build();
    }
}
