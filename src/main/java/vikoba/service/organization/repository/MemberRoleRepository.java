package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.organization.entity.MemberRole;
import vikoba.service.common.enums.GroupRole;

import java.util.List;

public interface MemberRoleRepository extends JpaRepository<MemberRole, Long> {
    List<MemberRole> findByGroupMemberIdAndActiveTrue(Long groupMemberId);

    boolean existsByGroupMemberMemberIdAndGroupMemberGroupOrganizationIdAndGroupMemberStatusAndRoleAndActiveTrue(
            Long memberId, Long organizationId, vikoba.service.common.enums.MembershipStatus status,
            GroupRole role);
}