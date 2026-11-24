package absolute_in_doubt.trading_platform.auth_service.repository;

import absolute_in_doubt.trading_platform.auth_service.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Short> {
    Optional<RoleEntity> findByName(String name);
}
