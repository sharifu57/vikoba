package vikoba.service.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.entity.SystemSetting;
import vikoba.service.common.repository.SystemSettingRepository;

@Service
@RequiredArgsConstructor
public class SystemSettingService {
    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, Boolean.toString(fallback)));
    }

    @Transactional(readOnly = true)
    public long getLong(String key, long fallback) {
        try {
            return Long.parseLong(get(key, Long.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
