package vikoba.service.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.social.entity.SocialFundContribution;

import java.util.List;

public interface SocialFundContributionRepository extends JpaRepository<SocialFundContribution, Long> {
    @Query("""
                SELECT s FROM SocialFundContribution s
                WHERE s.groupMember.id = :groupMemberId
                ORDER BY s.contributionDate DESC
            """)
    List<SocialFundContribution> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);
}
