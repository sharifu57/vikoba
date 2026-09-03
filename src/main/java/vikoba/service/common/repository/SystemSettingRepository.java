package vikoba.service.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.common.entity.SystemSetting;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
