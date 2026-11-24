package absolute_in_doubt.trading_platform.trading_service.config;

import invest.asset.v1.AssetServiceGrpc;
import invest.portfolio.v1.PortfolioServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Bean
    public AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub(GrpcChannelFactory channelFactory) {
        return AssetServiceGrpc.newBlockingStub(channelFactory.createChannel("asset-service"));
    }

    @Bean
    public PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub(GrpcChannelFactory channelFactory) {
        return PortfolioServiceGrpc.newBlockingStub(channelFactory.createChannel("portfolio-service"));
    }
}
