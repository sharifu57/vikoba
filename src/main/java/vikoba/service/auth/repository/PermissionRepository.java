package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.auth.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByName(String name);
}
