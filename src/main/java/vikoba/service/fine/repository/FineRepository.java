package vikoba.service.fine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.fine.entity.Fine;

import java.util.List;

public interface FineRepository extends JpaRepository<Fine, Long> {
    @Query("""
                SELECT f FROM Fine f
                WHERE f.groupMember.id = :groupMemberId
                ORDER BY f.issuedDate DESC
            """)
    List<Fine> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);
}
