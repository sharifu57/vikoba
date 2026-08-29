package vikoba.service.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.social.entity.SocialFundRequest;

import java.util.List;

public interface SocialFundRequestRepository extends JpaRepository<SocialFundRequest, Long> {
    @Query("""
            SELECT r FROM SocialFundRequest r
            JOIN FETCH r.groupMember gm
            JOIN FETCH gm.member m
            JOIN FETCH r.fundType ft
            WHERE gm.group.id = :groupId
            ORDER BY r.requestedDate DESC, r.id DESC
            """)
    List<SocialFundRequest> findByGroupId(@Param("groupId") Long groupId);
}
