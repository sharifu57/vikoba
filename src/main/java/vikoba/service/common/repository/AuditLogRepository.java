package vikoba.service.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.common.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
