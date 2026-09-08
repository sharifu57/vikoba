package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.contribution.entity.SharePurchaseRequestEntity;
import vikoba.service.contribution.entity.SharePurchaseRequestStatus;

import java.util.List;

public interface SharePurchaseRequestRepository extends JpaRepository<SharePurchaseRequestEntity, Long> {
    List<SharePurchaseRequestEntity> findByGroupMemberGroupIdAndStatusOrderBySubmittedAtDesc(
            Long groupId, SharePurchaseRequestStatus status);

    List<SharePurchaseRequestEntity> findByGroupMemberGroupIdOrderBySubmittedAtDesc(Long groupId);
}
