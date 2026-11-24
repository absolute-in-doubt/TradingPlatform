package absolute_in_doubt.trading_platform.auth_service.grpc;

import absolute_in_doubt.trading_platform.auth_service.entity.BrokerProfileEntity;
import absolute_in_doubt.trading_platform.auth_service.entity.InvestorProfileEntity;
import absolute_in_doubt.trading_platform.auth_service.entity.RoleEntity;
import absolute_in_doubt.trading_platform.auth_service.entity.UserEntity;
import absolute_in_doubt.trading_platform.auth_service.repository.InvestorProfileRepository;
import absolute_in_doubt.trading_platform.auth_service.repository.RoleRepository;
import absolute_in_doubt.trading_platform.auth_service.repository.UserRepository;
import absolute_in_doubt.trading_platform.auth_service.security.JwtProvider;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import invest.user.v1.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvestorProfileRepository investorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional(readOnly = true)
    public void getInvestor(GetInvestorRequest request, StreamObserver<GetInvestorResponse> responseObserver) {
        try {
            UUID investorId = UUID.fromString(request.getInvestorId());
            Optional<UserEntity> userOpt = userRepository.findById(investorId);

            if (userOpt.isEmpty()) {
                responseObserver.onNext(GetInvestorResponse.newBuilder().setFound(false).build());
                responseObserver.onCompleted();
                return;
            }

            UserEntity user = userOpt.get();
            UserStatus status = "ACTIVE".equalsIgnoreCase(user.getStatus())
                    ? UserStatus.USER_STATUS_ACTIVE
                    : UserStatus.USER_STATUS_BLOCKED;

            String brokerId = "";
            if (user.getInvestorProfile() != null && user.getInvestorProfile().getAssignedBrokerId() != null) {
                brokerId = user.getInvestorProfile().getAssignedBrokerId().toString();
            }

            GetInvestorResponse response = GetInvestorResponse.newBuilder()
                    .setFound(true)
                    .setStatus(status)
                    .setBrokerId(brokerId)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getInvestor", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void register(RegisterRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                responseObserver.onError(Status.ALREADY_EXISTS.withDescription("Email already in use").asRuntimeException());
                return;
            }

            String roleName = request.getRole().name();
            RoleEntity role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> Status.INVALID_ARGUMENT.withDescription("Role not found: " + roleName).asRuntimeException());

            UserEntity user = UserEntity.builder()
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .role(role)
                    .status("ACTIVE")
                    .build();

            if (request.getRole() == Role.INVESTOR) {
                UUID assignedBrokerId = null;
                if (!request.getBrokerId().isBlank()) {
                    assignedBrokerId = UUID.fromString(request.getBrokerId());
                }
                String riskProfile = request.getRiskProfile() != RiskProfile.RISK_PROFILE_UNSPECIFIED
                        ? request.getRiskProfile().name()
                        : "MODERATE";

                InvestorProfileEntity profile = InvestorProfileEntity.builder()
                        .user(user)
                        .assignedBrokerId(assignedBrokerId)
                        .riskProfile(riskProfile)
                        .investmentExperienceYears(0)
                        .build();
                user.setInvestorProfile(profile);
            } else if (request.getRole() == Role.BROKER) {
                BrokerProfileEntity profile = BrokerProfileEntity.builder()
                        .user(user)
                        .licenseNumber(request.getLicenseNumber())
                        .specialization(request.getSpecialization())
                        .maxInvestors(50)
                        .build();
                user.setBrokerProfile(profile);
            }

            UserEntity savedUser = userRepository.save(user);

            String token = jwtProvider.generateToken(savedUser.getId(), savedUser.getEmail(), roleName);
            Instant expiry = jwtProvider.getExpiryInstant();

            AuthResponse response = AuthResponse.newBuilder()
                    .setAccessToken(token)
                    .setExpiresAt(expiry.toString())
                    .setUser(mapToUserResponse(savedUser))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in register", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        try {
            UserEntity user = userRepository.findByEmail(request.getEmail())
                    .orElse(null);

            if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Invalid email or password").asRuntimeException());
                return;
            }

            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Account is blocked").asRuntimeException());
                return;
            }

            String roleName = user.getRole().getName();
            String token = jwtProvider.generateToken(user.getId(), user.getEmail(), roleName);
            Instant expiry = jwtProvider.getExpiryInstant();

            AuthResponse response = AuthResponse.newBuilder()
                    .setAccessToken(token)
                    .setExpiresAt(expiry.toString())
                    .setUser(mapToUserResponse(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in login", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("User not found: " + userId).asRuntimeException());

            responseObserver.onNext(mapToUserResponse(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getUser", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listBrokerInvestors(ListBrokerInvestorsRequest request, StreamObserver<ListBrokerInvestorsResponse> responseObserver) {
        try {
            UUID brokerId = UUID.fromString(request.getBrokerId());
            List<InvestorProfileEntity> profiles = investorProfileRepository.findByAssignedBrokerId(brokerId);

            ListBrokerInvestorsResponse.Builder builder = ListBrokerInvestorsResponse.newBuilder();
            for (InvestorProfileEntity profile : profiles) {
                builder.addInvestors(mapToUserResponse(profile.getUser()));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in listBrokerInvestors", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private UserResponse mapToUserResponse(UserEntity user) {
        UserResponse.Builder builder = UserResponse.newBuilder()
                .setId(user.getId().toString())
                .setEmail(user.getEmail())
                .setFullName(user.getFullName())
                .setStatus("ACTIVE".equalsIgnoreCase(user.getStatus()) ? UserStatus.USER_STATUS_ACTIVE : UserStatus.USER_STATUS_BLOCKED)
                .setRegisteredAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : Instant.now().toString());

        try {
            builder.setRole(Role.valueOf(user.getRole().getName()));
        } catch (Exception e) {
            builder.setRole(Role.ROLE_UNSPECIFIED);
        }

        if (user.getInvestorProfile() != null) {
            InvestorProfileEntity ip = user.getInvestorProfile();
            InvestorProfileResponse.Builder ipBuilder = InvestorProfileResponse.newBuilder();
            try {
                ipBuilder.setRiskProfile(RiskProfile.valueOf(ip.getRiskProfile()));
            } catch (Exception e) {
                ipBuilder.setRiskProfile(RiskProfile.MODERATE);
            }
            LocalDate investDate = ip.getCreatedAt() != null
                    ? LocalDate.ofInstant(ip.getCreatedAt(), ZoneOffset.UTC)
                    : LocalDate.now();
            ipBuilder.setInvestingSince(investDate.toString());

            if (ip.getAssignedBrokerId() != null) {
                userRepository.findById(ip.getAssignedBrokerId()).ifPresent(broker -> {
                    UserResponse.Builder brokerBuilder = UserResponse.newBuilder()
                            .setId(broker.getId().toString())
                            .setEmail(broker.getEmail())
                            .setFullName(broker.getFullName())
                            .setRole(Role.BROKER)
                            .setStatus("ACTIVE".equalsIgnoreCase(broker.getStatus()) ? UserStatus.USER_STATUS_ACTIVE : UserStatus.USER_STATUS_BLOCKED);
                    ipBuilder.setBroker(brokerBuilder.build());
                });
            }
            builder.setInvestorProfile(ipBuilder.build());
        }

        if (user.getBrokerProfile() != null) {
            BrokerProfileEntity bp = user.getBrokerProfile();
            BrokerProfileResponse.Builder bpBuilder = BrokerProfileResponse.newBuilder()
                    .setLicenseNumber(bp.getLicenseNumber() != null ? bp.getLicenseNumber() : "")
                    .setSpecialization(bp.getSpecialization() != null ? bp.getSpecialization() : "");
            builder.setBrokerProfile(bpBuilder.build());
        }

        return builder.build();
    }
}
