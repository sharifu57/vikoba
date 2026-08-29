package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.organization.entity.VikobaGroup;

import java.util.List;
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

    @Query("""
        SELECT DISTINCT g
        FROM VikobaGroup g
        JOIN g.members gm
        WHERE gm.member.id = :memberId
    """)
    List<VikobaGroup> findGroupsByMemberId(
            @Param("memberId") Long memberId
    );

    @Query("""
    SELECT DISTINCT g
    FROM VikobaGroup g
    JOIN g.members gm
    WHERE gm.member.id = :memberId
      AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
""")
    List<VikobaGroup> findActiveGroupsByMemberId(
            @Param("memberId") Long memberId
    );

    @Query("""
    SELECT g
    FROM VikobaGroup g
    JOIN FETCH g.organization
    WHERE g.id = :groupId
""")
    Optional<VikobaGroup> findByIdWithOrganization(@Param("groupId") Long groupId);
}
