package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.organization.entity.GroupMember;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    Optional<GroupMember> findByGroupIdAndMemberId(
            Long groupId,
            Long memberId
    );

    boolean existsByGroupIdAndMemberId(
            Long groupId,
            Long memberId
    );

    List<GroupMember> findByGroupIdAndStatus(
            Long groupId,
            MembershipStatus status
    );
}
