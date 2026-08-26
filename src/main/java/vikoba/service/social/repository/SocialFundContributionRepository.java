package vikoba.service.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.social.entity.SocialFundContribution;

import java.util.List;

public interface SocialFundContributionRepository extends JpaRepository<SocialFundContribution, Long> {
    @Query("""
            SELECT s FROM SocialFundContribution s
            JOIN FETCH s.groupMember gm
            JOIN FETCH gm.member m
            JOIN FETCH s.fundType ft
            WHERE gm.group.id = :groupId
            ORDER BY s.contributionDate DESC, s.id DESC
            """)
    List<SocialFundContribution> findByGroupId(@Param("groupId") Long groupId);

    @Query("""
                SELECT s FROM SocialFundContribution s
                WHERE s.groupMember.id = :groupMemberId
                ORDER BY s.contributionDate DESC
            """)
    List<SocialFundContribution> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);
}
