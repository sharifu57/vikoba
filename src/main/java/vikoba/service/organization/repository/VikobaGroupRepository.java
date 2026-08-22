package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.VikobaGroup;

import java.util.Optional;

public interface VikobaGroupRepository extends JpaRepository<VikobaGroup, Long> {
    Optional<VikobaGroup> findByOrganizationIdAndCode(
            Long organizationId,
            String code
    );

    boolean existsByOrganizationIdAndCode(
            Long organizationId,
            String code
    );
}
