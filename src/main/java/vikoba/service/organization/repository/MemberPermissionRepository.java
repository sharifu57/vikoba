package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.MemberPermission;
import java.util.List;

public interface MemberPermissionRepository extends JpaRepository<MemberPermission, Long> {
    List<MemberPermission> findByGroupMemberId(Long groupMemberId);
    void deleteByGroupMemberId(Long groupMemberId);
}
