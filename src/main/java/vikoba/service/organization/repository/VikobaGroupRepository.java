package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.organization.entity.VikobaGroup;

import java.util.List;
import java.util.Optional;

public interface VikobaGroupRepository extends JpaRepository<VikobaGroup, Long> {
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("select g from VikobaGroup g where g.id = :id")
        Optional<VikobaGroup> findByIdForUpdate(@Param("id") Long id);
        @Query("""
            select count(g) from VikobaGroup g where
            (select count(r) from MemberRole r where r.groupMember.group = g and r.active = true
                and r.role in ('GROUP_CHAIRMAN', 'CHAIRPERSON')) <> 1
            or exists (select r.id from MemberRole r where r.groupMember.group = g and r.active = true
                and r.role in ('GROUP_CHAIRMAN', 'CHAIRPERSON')
                and (r.groupMember.status <> 'ACTIVE' or r.groupMember.member.status <> 'ACTIVE'))
            """)
        long countLeadershipIssues();
        org.springframework.data.domain.Page<VikobaGroup> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
                String name, String code, org.springframework.data.domain.Pageable pageable);
        @Query("select count(g) from VikobaGroup g where g.status = vikoba.service.common.enums.VikobaGroupStatus.ACTIVE")
        long countActiveGroups();

        Optional<VikobaGroup> findByOrganizationIdAndCode(
                        Long organizationId,
                        String code);

        boolean existsByOrganizationIdAndCode(
                        Long organizationId,
                        String code);

        @Query("""
                            SELECT DISTINCT g
                            FROM VikobaGroup g
                            JOIN g.members gm
                            WHERE gm.member.id = :memberId
                        """)
        List<VikobaGroup> findGroupsByMemberId(
                        @Param("memberId") Long memberId);

        @Query("""
                            SELECT DISTINCT g
                            FROM VikobaGroup g
                            JOIN g.members gm
                            WHERE gm.member.id = :memberId
                              AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
                        """)
        List<VikobaGroup> findActiveGroupsByMemberId(
                        @Param("memberId") Long memberId);

        @Query("""
                            SELECT g
                            FROM VikobaGroup g
                            JOIN FETCH g.organization
                            WHERE g.id = :groupId
                        """)
        Optional<VikobaGroup> findByIdWithOrganization(@Param("groupId") Long groupId);
}
