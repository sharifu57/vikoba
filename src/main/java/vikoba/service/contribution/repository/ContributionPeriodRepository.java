package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.contribution.entity.ContributionPeriod;

import java.util.List;

public interface ContributionPeriodRepository extends JpaRepository<ContributionPeriod, Long> {

    /**
     * Find all active contribution periods for a group
     */
    @Query("""
                SELECT cp FROM ContributionPeriod cp
                WHERE cp.contributionType.group.id = :groupId
                AND cp.status = 'OPEN'
                ORDER BY cp.periodStart DESC
            """)
    List<ContributionPeriod> findActiveByGroupId(@Param("groupId") Long groupId);

    /**
     * Find contribution period by type and time range
     */
    @Query("""
                SELECT cp FROM ContributionPeriod cp
                WHERE cp.contributionType.id = :contributionTypeId
                AND YEAR(cp.periodStart) = :year
                AND MONTH(cp.periodStart) = :month
            """)
    ContributionPeriod findByTypeAndYearMonth(
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("year") Integer year,
            @Param("month") Integer month);
}
