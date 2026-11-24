package absolute_in_doubt.trading_platform.asset_service.config;

import absolute_in_doubt.trading_platform.asset_service.entity.AssetCategoryEntity;
import absolute_in_doubt.trading_platform.asset_service.entity.AssetEntity;
import absolute_in_doubt.trading_platform.asset_service.repository.AssetCategoryRepository;
import absolute_in_doubt.trading_platform.asset_service.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AssetCategoryRepository categoryRepository;
    private final AssetRepository assetRepository;

    @Override
    @Transactional
    public void run(String... args) {
        AssetCategoryEntity stocks = categoryRepository.findByCode("STOCKS")
                .orElseGet(() -> categoryRepository.save(AssetCategoryEntity.builder()
                        .code("STOCKS").name("Акции").description("Акции публичных компаний").build()));

        AssetCategoryEntity bonds = categoryRepository.findByCode("BONDS")
                .orElseGet(() -> categoryRepository.save(AssetCategoryEntity.builder()
                        .code("BONDS").name("Облигации").description("Государственные и корпоративные облигации").build()));

        AssetCategoryEntity etfs = categoryRepository.findByCode("ETFS")
                .orElseGet(() -> categoryRepository.save(AssetCategoryEntity.builder()
                        .code("ETFS").name("Фонды").description("Биржевые инвестиционные фонды").build()));

        if (!assetRepository.existsByTicker("AAPL")) {
            assetRepository.save(AssetEntity.builder()
                    .ticker("AAPL")
                    .name("Apple Inc.")
                    .category(stocks)
                    .currentPrice(new BigDecimal("182.5000"))
                    .currency("USD")
                    .isActive(true)
                    .build());
            log.info("Initialized demo asset AAPL");
        }

        if (!assetRepository.existsByTicker("GOOGL")) {
            assetRepository.save(AssetEntity.builder()
                    .ticker("GOOGL")
                    .name("Alphabet Inc.")
                    .category(stocks)
                    .currentPrice(new BigDecimal("175.2000"))
                    .currency("USD")
                    .isActive(true)
                    .build());
            log.info("Initialized demo asset GOOGL");
        }

        if (!assetRepository.existsByTicker("US10Y")) {
            assetRepository.save(AssetEntity.builder()
                    .ticker("US10Y")
                    .name("US Treasury 10Y Bond")
                    .category(bonds)
                    .currentPrice(new BigDecimal("98.4000"))
                    .currency("USD")
                    .isActive(true)
                    .build());
            log.info("Initialized demo asset US10Y");
        }
    }
}
