package absolute_in_doubt.trading_platform.payment_service.config;

import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.user.v1.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceStub(GrpcChannelFactory channelFactory) {
        return UserServiceGrpc.newBlockingStub(channelFactory.createChannel("user-service"));
    }

    @Bean
    public PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub(GrpcChannelFactory channelFactory) {
        return PortfolioServiceGrpc.newBlockingStub(channelFactory.createChannel("portfolio-service"));
    }
}
