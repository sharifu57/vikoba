package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.organization.entity.GroupMember;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
        Optional<GroupMember> findByGroupIdAndMemberId(
                        Long groupId,
                        Long memberId);

        boolean existsByGroupIdAndMemberId(
                        Long groupId,
                        Long memberId);

        List<GroupMember> findByGroupIdAndStatus(
                        Long groupId,
                        MembershipStatus status);

        Long countByGroupId(Long groupId);

        @Query("""
                            SELECT COUNT(gm)
                            FROM GroupMember gm
                            WHERE gm.group.id = :groupId
                            AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
                        """)
        Long countActiveMembersByGroupId(@Param("groupId") Long groupId);

        @Query("""
                            SELECT gm
                            FROM GroupMember gm
                            JOIN FETCH gm.group g
                            JOIN FETCH g.organization
                            WHERE gm.member.id = :memberId
                            AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
                            ORDER BY gm.id ASC
                        """)
        List<GroupMember> findActiveGroupsByMemberId(
                        @Param("memberId") Long memberId);

}
