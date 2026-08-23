package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.GroupSettings;

import java.util.Optional;

public interface GroupSettingsRepository extends JpaRepository<GroupSettings, Long> {
    Optional<GroupSettings> findByGroupId(Long groupId);

}