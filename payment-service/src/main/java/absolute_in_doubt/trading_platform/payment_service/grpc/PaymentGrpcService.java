package absolute_in_doubt.trading_platform.payment_service.grpc;

import absolute_in_doubt.trading_platform.payment_service.entity.PaymentOperationEntity;
import absolute_in_doubt.trading_platform.payment_service.event.EventEnvelope;
import absolute_in_doubt.trading_platform.payment_service.event.PaymentProcessedPayload;
import absolute_in_doubt.trading_platform.payment_service.repository.PaymentOperationRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.payment.v1.*;
import invest.portfolio.v1.CheckFundsRequest;
import invest.portfolio.v1.CheckFundsResponse;
import invest.portfolio.v1.PortfolioServiceGrpc;
import invest.user.v1.GetInvestorRequest;
import invest.user.v1.GetInvestorResponse;
import invest.user.v1.UserServiceGrpc;
import invest.user.v1.UserStatus;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentOperationRepository paymentRepository;
    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;
    private final PortfolioServiceGrpc.PortfolioServiceBlockingStub portfolioServiceStub;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-processed:payment.payment-processed}")
    private String paymentProcessedTopic;

    @Override
    @Transactional
    public void registerPayment(RegisterPaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        try {
            UUID investorId = UUID.fromString(request.getInvestorId());
            BigDecimal amount = new BigDecimal(request.getAmount());
            String currency = request.getCurrency().toUpperCase().trim();
            String opType = request.getType() == PaymentType.DEPOSIT ? "DEPOSIT" : "WITHDRAWAL";

            // 1. Verify Investor with Auth Service
            GetInvestorResponse investorResp;
            try {
                investorResp = userServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .getInvestor(GetInvestorRequest.newBuilder().setInvestorId(request.getInvestorId()).build());
            } catch (Exception e) {
                log.error("Failed to call UserService", e);
                responseObserver.onError(Status.UNAVAILABLE.withDescription("User Service is temporarily unavailable").asRuntimeException());
                return;
            }

            if (!investorResp.getFound()) {
                PaymentOperationEntity rejected = PaymentOperationEntity.builder()
                        .investorId(investorId)
                        .operationType(opType)
                        .amount(amount)
                        .currency(currency)
                        .status("REJECTED")
                        .rejectReason("INVESTOR_NOT_FOUND")
                        .processedAt(Instant.now())
                        .build();
                PaymentOperationEntity saved = paymentRepository.save(rejected);
                responseObserver.onNext(mapToPaymentResponse(saved));
                responseObserver.onCompleted();
                return;
            }

            if (investorResp.getStatus() == UserStatus.USER_STATUS_BLOCKED) {
                PaymentOperationEntity rejected = PaymentOperationEntity.builder()
                        .investorId(investorId)
                        .operationType(opType)
                        .amount(amount)
                        .currency(currency)
                        .status("REJECTED")
                        .rejectReason("INVESTOR_BLOCKED")
                        .processedAt(Instant.now())
                        .build();
                PaymentOperationEntity saved = paymentRepository.save(rejected);
                responseObserver.onNext(mapToPaymentResponse(saved));
                responseObserver.onCompleted();
                return;
            }

            // 2. For WITHDRAWAL, check funds in Portfolio Service
            if (request.getType() == PaymentType.WITHDRAWAL) {
                CheckFundsResponse fundsResp;
                try {
                    fundsResp = portfolioServiceStub.withDeadlineAfter(2, TimeUnit.SECONDS)
                            .checkFunds(CheckFundsRequest.newBuilder()
                                    .setInvestorId(request.getInvestorId())
                                    .setCurrency(currency)
                                    .setAmount(amount.toPlainString())
                                    .build());
                } catch (Exception e) {
                    log.error("Failed to call PortfolioService", e);
                    responseObserver.onError(Status.UNAVAILABLE.withDescription("Portfolio Service is temporarily unavailable").asRuntimeException());
                    return;
                }

                if (!fundsResp.getSufficient()) {
                    PaymentOperationEntity rejected = PaymentOperationEntity.builder()
                            .investorId(investorId)
                            .operationType(opType)
                            .amount(amount)
                            .currency(currency)
                            .status("REJECTED")
                            .rejectReason("INSUFFICIENT_FUNDS")
                            .processedAt(Instant.now())
                            .build();
                    PaymentOperationEntity saved = paymentRepository.save(rejected);
                    responseObserver.onNext(mapToPaymentResponse(saved));
                    responseObserver.onCompleted();
                    return;
                }
            }

            // 3. Process payment
            PaymentOperationEntity operation = PaymentOperationEntity.builder()
                    .investorId(investorId)
                    .operationType(opType)
                    .amount(amount)
                    .currency(currency)
                    .status("PROCESSED")
                    .rejectReason(null)
                    .paymentMethod("BANK_TRANSFER")
                    .processedAt(Instant.now())
                    .build();

            PaymentOperationEntity saved = paymentRepository.save(operation);

            // 4. Publish PaymentProcessed event
            PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                    .paymentId(saved.getId().toString())
                    .investorId(saved.getInvestorId().toString())
                    .operationType(saved.getOperationType())
                    .amount(saved.getAmount().toPlainString())
                    .currency(saved.getCurrency())
                    .processedAt(saved.getProcessedAt().toString())
                    .build();

            EventEnvelope<PaymentProcessedPayload> envelope = EventEnvelope.<PaymentProcessedPayload>builder()
                    .eventType("PaymentProcessed")
                    .producer("payment-service")
                    .payload(payload)
                    .build();

            kafkaTemplate.send(paymentProcessedTopic, saved.getInvestorId().toString(), envelope);

            responseObserver.onNext(mapToPaymentResponse(saved));
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in registerPayment", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listPayments(ListPaymentsRequest request, StreamObserver<ListPaymentsResponse> responseObserver) {
        try {
            Specification<PaymentOperationEntity> spec = (root, query, cb) -> cb.conjunction();

            if (!request.getInvestorId().isBlank()) {
                UUID iid = UUID.fromString(request.getInvestorId());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("investorId"), iid));
            }
            if (request.getType() != PaymentType.PAYMENT_TYPE_UNSPECIFIED) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("operationType"), request.getType().name()));
            }
            if (!request.getDateFrom().isBlank()) {
                Instant from = Instant.parse(request.getDateFrom());
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("processedAt"), from));
            }
            if (!request.getDateTo().isBlank()) {
                Instant to = Instant.parse(request.getDateTo());
                spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("processedAt"), to));
            }

            Sort sort = Sort.by("processedAt").descending();
            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            int offset = Math.max(0, request.getOffset());
            int pageNumber = offset / limit;

            Page<PaymentOperationEntity> page = paymentRepository.findAll(spec, PageRequest.of(pageNumber, limit, sort));

            ListPaymentsResponse.Builder builder = ListPaymentsResponse.newBuilder()
                    .setTotalCount((int) page.getTotalElements());

            for (PaymentOperationEntity op : page.getContent()) {
                builder.addItems(mapToPaymentResponse(op));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in listPayments", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private PaymentResponse mapToPaymentResponse(PaymentOperationEntity op) {
        PaymentResponse.Builder builder = PaymentResponse.newBuilder()
                .setId(op.getId().toString())
                .setInvestorId(op.getInvestorId().toString())
                .setAmount(op.getAmount().toPlainString())
                .setCurrency(op.getCurrency())
                .setRejectReason(op.getRejectReason() != null ? op.getRejectReason() : "")
                .setPaymentMethod(op.getPaymentMethod() != null ? op.getPaymentMethod() : "")
                .setProcessedAt(op.getProcessedAt() != null ? op.getProcessedAt().toString() : Instant.now().toString());

        try {
            builder.setType(PaymentType.valueOf(op.getOperationType()));
        } catch (Exception e) {
            builder.setType(PaymentType.PAYMENT_TYPE_UNSPECIFIED);
        }

        try {
            builder.setStatus(PaymentStatus.valueOf(op.getStatus()));
        } catch (Exception e) {
            builder.setStatus(PaymentStatus.PAYMENT_STATUS_UNSPECIFIED);
        }

        return builder.build();
    }
}
