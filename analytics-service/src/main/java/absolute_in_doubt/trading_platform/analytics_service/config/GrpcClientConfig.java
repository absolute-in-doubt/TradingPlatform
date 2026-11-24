package absolute_in_doubt.trading_platform.analytics_service.config;

import invest.portfolio.v1.PortfolioServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub(GrpcChannelFactory channelFactory) {
        return PortfolioServiceGrpc.newBlockingStub(channelFactory.createChannel("portfolio-service"));
    }
}
