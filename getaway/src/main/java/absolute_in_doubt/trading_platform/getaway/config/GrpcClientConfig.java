package absolute_in_doubt.trading_platform.getaway.config;

import invest.analytics.v1.AnalyticsServiceGrpc;
import invest.asset.v1.AssetServiceGrpc;
import invest.notification.v1.NotificationServiceGrpc;
import invest.payment.v1.PaymentServiceGrpc;
import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.trading.v1.TradingServiceGrpc;
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
    public AssetServiceGrpc.AssetServiceBlockingStub assetServiceStub(GrpcChannelFactory channelFactory) {
        return AssetServiceGrpc.newBlockingStub(channelFactory.createChannel("asset-service"));
    }

    @Bean
    public PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub(GrpcChannelFactory channelFactory) {
        return PortfolioServiceGrpc.newBlockingStub(channelFactory.createChannel("portfolio-service"));
    }

    @Bean
    public TradingServiceGrpc.TradingServiceBlockingStub tradingServiceStub(GrpcChannelFactory channelFactory) {
        return TradingServiceGrpc.newBlockingStub(channelFactory.createChannel("trading-service"));
    }

    @Bean
    public PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub(GrpcChannelFactory channelFactory) {
        return PaymentServiceGrpc.newBlockingStub(channelFactory.createChannel("payment-service"));
    }

    @Bean
    public AnalyticsServiceGrpc.AnalyticsServiceBlockingStub analyticsServiceStub(GrpcChannelFactory channelFactory) {
        return AnalyticsServiceGrpc.newBlockingStub(channelFactory.createChannel("analytics-service"));
    }

    @Bean
    public NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceStub(GrpcChannelFactory channelFactory) {
        return NotificationServiceGrpc.newBlockingStub(channelFactory.createChannel("notification-service"));
    }
}
