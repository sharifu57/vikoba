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
}
