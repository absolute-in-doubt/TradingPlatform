package absolute_in_doubt.trading_platform.getaway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

public class AuthDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private String id;
        private String email;
        private String fullName;
        private String role;
        private String status;
        private Instant registeredAt;
        private InvestorProfileDto investorProfile;
        private BrokerProfileDto brokerProfile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestorProfileDto {
        private String riskProfile;
        private LocalDate investingSince;
        private UserDto broker;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerProfileDto {
        private String licenseNumber;
        private String specialization;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthPayloadDto {
        private String accessToken;
        private Instant expiresAt;
        private UserDto user;
    }

    public record RegisterUserInput(
            String email,
            String password,
            String fullName,
            String role,
            String riskProfile,
            String brokerId,
            String licenseNumber,
            String specialization
    ) {}

    public record LoginInput(
            String email,
            String password
    ) {}
}
