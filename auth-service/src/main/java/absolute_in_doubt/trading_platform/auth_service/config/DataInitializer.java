package absolute_in_doubt.trading_platform.auth_service.config;

import absolute_in_doubt.trading_platform.auth_service.entity.RoleEntity;
import absolute_in_doubt.trading_platform.auth_service.entity.UserEntity;
import absolute_in_doubt.trading_platform.auth_service.repository.RoleRepository;
import absolute_in_doubt.trading_platform.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        RoleEntity investorRole = roleRepository.findByName("INVESTOR")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().name("INVESTOR").description("Individual Investor").build()));

        RoleEntity brokerRole = roleRepository.findByName("BROKER")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().name("BROKER").description("Broker Consultant").build()));

        RoleEntity adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> roleRepository.save(RoleEntity.builder().name("ADMIN").description("System Administrator").build()));

        if (!userRepository.existsByEmail("admin@platform.com")) {
            UserEntity admin = UserEntity.builder()
                    .email("admin@platform.com")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .fullName("Platform Administrator")
                    .role(adminRole)
                    .status("ACTIVE")
                    .build();
            userRepository.save(admin);
            log.info("Initialized default admin account: admin@platform.com / admin");
        }
    }
}
