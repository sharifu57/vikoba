package vikoba.service.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.organization.entity.GroupMember;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @Query("""
        select gm from GroupMember gm join fetch gm.member m join fetch gm.group g join fetch g.organization
        where (:groupId is null or g.id = :groupId)
          and (lower(concat(m.firstName, ' ', m.lastName)) like :search
            or lower(coalesce(m.phone, '')) like :search or lower(g.name) like :search
            or lower(gm.membershipNumber) like :search)
        """)
    org.springframework.data.domain.Page<GroupMember> searchSystemMembers(
        @Param("groupId") Long groupId, @Param("search") String search,
        org.springframework.data.domain.Pageable pageable);
    @Query("select count(gm) from GroupMember gm where gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE")
    long countActiveMembers();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select gm from GroupMember gm where gm.id = :id")
    Optional<GroupMember> findByIdForUpdate(@Param("id") Long id);

    Optional<GroupMember> findByGroupIdAndMemberId(
            Long groupId,
            Long memberId);

    boolean existsByGroupIdAndMemberId(
            Long groupId,
            Long memberId);

    List<GroupMember> findByGroupIdAndStatus(
            Long groupId,
            MembershipStatus status);

    List<GroupMember> findByGroupId(Long groupId);

    Long countByGroupId(Long groupId);

    @Query("""
                SELECT COUNT(gm)
                FROM GroupMember gm
                WHERE gm.group.id = :groupId
                AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
            """)
    Long countActiveMembersByGroupId(@Param("groupId") Long groupId);

    @Query("""
                SELECT gm
                FROM GroupMember gm
                JOIN FETCH gm.group g
                JOIN FETCH g.organization
                WHERE gm.member.id = :memberId
                AND gm.status = vikoba.service.common.enums.MembershipStatus.ACTIVE
                ORDER BY gm.id ASC
            """)
    List<GroupMember> findActiveGroupsByMemberId(
            @Param("memberId") Long memberId);

}
