package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.CommonDtos;
import absolute_in_doubt.trading_platform.getaway.dto.PaymentDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import absolute_in_doubt.trading_platform.getaway.security.SecurityUtils;
import absolute_in_doubt.trading_platform.getaway.security.UserPrincipal;
import invest.payment.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    @QueryMapping
    public PaymentDtos.PaymentPageDto payments(
            @Argument(name = "filter") PaymentDtos.PaymentFilter filter,
            @Argument(name = "page") CommonDtos.PageInput page,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        ListPaymentsRequest.Builder builder = ListPaymentsRequest.newBuilder()
                .setInvestorId(currentUser.getUserId().toString());

        if (filter != null) {
            if (filter.type() != null) {
                builder.setType(PaymentType.valueOf(filter.type().toUpperCase()));
            }
            if (filter.dateFrom() != null) builder.setDateFrom(filter.dateFrom().toString());
            if (filter.dateTo() != null) builder.setDateTo(filter.dateTo().toString());
        }

        int limit = page != null ? page.getEffectiveLimit() : 20;
        int offset = page != null ? page.getEffectiveOffset() : 0;
        builder.setLimit(limit).setOffset(offset);

        ListPaymentsResponse response = paymentServiceStub.listPayments(builder.build());
        List<PaymentDtos.PaymentOperationDto> items = response.getItemsList().stream()
                .map(ProtoMapper::toPaymentDto)
                .toList();

        return PaymentDtos.PaymentPageDto.builder()
                .items(items)
                .totalCount(response.getTotalCount())
                .build();
    }

    @MutationMapping
    public PaymentDtos.PaymentOperationDto depositFunds(
            @Argument PaymentDtos.PaymentInput input,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        RegisterPaymentRequest request = RegisterPaymentRequest.newBuilder()
                .setInvestorId(currentUser.getUserId().toString())
                .setType(PaymentType.DEPOSIT)
                .setAmount(input.amount().toPlainString())
                .setCurrency(input.currency())
                .build();

        PaymentResponse response = paymentServiceStub.registerPayment(request);
        return ProtoMapper.toPaymentDto(response);
    }

    @MutationMapping
    public PaymentDtos.PaymentOperationDto withdrawFunds(
            @Argument PaymentDtos.PaymentInput input,
            @ContextValue(name = "currentUser", required = false) UserPrincipal currentUser
    ) {
        SecurityUtils.requireUser(currentUser);

        RegisterPaymentRequest request = RegisterPaymentRequest.newBuilder()
                .setInvestorId(currentUser.getUserId().toString())
                .setType(PaymentType.WITHDRAWAL)
                .setAmount(input.amount().toPlainString())
                .setCurrency(input.currency())
                .build();

        PaymentResponse response = paymentServiceStub.registerPayment(request);
        return ProtoMapper.toPaymentDto(response);
    }
}
