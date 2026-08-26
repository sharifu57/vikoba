package vikoba.service.fine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.fine.entity.Fine;

import java.util.List;

public interface FineRepository extends JpaRepository<Fine, Long> {
    @Query("""
                SELECT f FROM Fine f
                JOIN FETCH f.groupMember gm
                JOIN FETCH gm.member m
                JOIN FETCH f.fineType ft
                WHERE gm.group.id = :groupId
                ORDER BY f.issuedDate DESC, f.id DESC
            """)
    List<Fine> findByGroupId(@Param("groupId") Long groupId);

    @Query("""
                SELECT f FROM Fine f
                WHERE f.groupMember.id = :groupMemberId
                ORDER BY f.issuedDate DESC
            """)
    List<Fine> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);
}
