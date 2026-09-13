package vikoba.service.contribution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import vikoba.service.contribution.entity.SharePurchaseRequestEntity;
import vikoba.service.contribution.entity.SharePurchaseRequestStatus;

import java.util.List;

public interface SharePurchaseRequestRepository extends JpaRepository<SharePurchaseRequestEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SharePurchaseRequestEntity> findWithLockById(Long id);
    List<SharePurchaseRequestEntity> findByGroupMemberGroupIdAndStatusOrderBySubmittedAtDesc(
            Long groupId, SharePurchaseRequestStatus status);

    List<SharePurchaseRequestEntity> findByGroupMemberGroupIdOrderBySubmittedAtDesc(Long groupId);

    List<SharePurchaseRequestEntity> findByGroupMemberIdOrderBySubmittedAtDesc(Long groupMemberId);
}
