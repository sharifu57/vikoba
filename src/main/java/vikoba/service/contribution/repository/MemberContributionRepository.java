package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.contribution.entity.MemberContribution;

import java.math.BigDecimal;

public interface MemberContributionRepository extends JpaRepository<MemberContribution, Long> {
    @Query("""
                SELECT COALESCE(SUM(mc.paidAmount), 0)
                FROM MemberContribution mc
                WHERE mc.groupMember.group.id = :groupId
            """)
    BigDecimal sumPaidAmountByGroupId(@Param("groupId") Long groupId);

    @Query("""
                SELECT mc FROM MemberContribution mc
                JOIN FETCH mc.groupMember gm
                JOIN FETCH gm.member m
                JOIN FETCH mc.contributionPeriod cp
                JOIN FETCH cp.contributionType ct
                WHERE gm.id = :groupMemberId
                ORDER BY cp.periodStart DESC
            """)
    java.util.List<MemberContribution> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);

    @Query("""
                SELECT mc FROM MemberContribution mc
                JOIN FETCH mc.groupMember gm
                JOIN FETCH gm.member m
                JOIN FETCH mc.contributionPeriod cp
                JOIN FETCH cp.contributionType ct
                WHERE gm.group.id = :groupId
                ORDER BY cp.periodStart DESC, m.firstName ASC
            """)
    java.util.List<MemberContribution> findByGroupId(@Param("groupId") Long groupId);
}
