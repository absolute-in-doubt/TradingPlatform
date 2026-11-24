package absolute_in_doubt.trading_platform.getaway.controller;

import absolute_in_doubt.trading_platform.getaway.dto.AuthDtos;
import absolute_in_doubt.trading_platform.getaway.mapper.ProtoMapper;
import absolute_in_doubt.trading_platform.getaway.security.SecurityUtils;
import absolute_in_doubt.trading_platform.getaway.security.UserPrincipal;
import invest.user.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    @MutationMapping
    public AuthDtos.AuthPayloadDto registerUser(@Argument AuthDtos.RegisterUserInput input) {
        RegisterRequest.Builder builder = RegisterRequest.newBuilder()
                .setEmail(input.email())
                .setPassword(input.password())
                .setFullName(input.fullName())
                .setRole(Role.valueOf(input.role().toUpperCase()));

        if (input.riskProfile() != null && !input.riskProfile().isBlank()) {
            builder.setRiskProfile(RiskProfile.valueOf(input.riskProfile().toUpperCase()));
        }
        if (input.brokerId() != null && !input.brokerId().isBlank()) {
            builder.setBrokerId(input.brokerId());
        }
        if (input.licenseNumber() != null && !input.licenseNumber().isBlank()) {
            builder.setLicenseNumber(input.licenseNumber());
        }
        if (input.specialization() != null && !input.specialization().isBlank()) {
            builder.setSpecialization(input.specialization());
        }

        AuthResponse response = userServiceStub.register(builder.build());
        return ProtoMapper.toAuthPayloadDto(response);
    }

    @MutationMapping
    public AuthDtos.AuthPayloadDto login(@Argument AuthDtos.LoginInput input) {
        LoginRequest request = LoginRequest.newBuilder()
                .setEmail(input.email())
                .setPassword(input.password())
                .build();

        AuthResponse response = userServiceStub.login(request);
        return ProtoMapper.toAuthPayloadDto(response);
    }

    @QueryMapping
    public AuthDtos.UserDto me(@ContextValue(name = "currentUser", required = false) UserPrincipal currentUser) {
        SecurityUtils.requireUser(currentUser);
        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(currentUser.getUserId().toString())
                .build();

        UserResponse response = userServiceStub.getUser(request);
        return ProtoMapper.toUserDto(response);
    }

    @QueryMapping
    public List<AuthDtos.UserDto> brokerInvestors(@ContextValue(name = "currentUser", required = false) UserPrincipal currentUser) {
        SecurityUtils.requireRole(currentUser, "BROKER");
        ListBrokerInvestorsRequest request = ListBrokerInvestorsRequest.newBuilder()
                .setBrokerId(currentUser.getUserId().toString())
                .build();

        ListBrokerInvestorsResponse response = userServiceStub.listBrokerInvestors(request);
        return response.getInvestorsList().stream()
                .map(ProtoMapper::toUserDto)
                .toList();
    }
}
