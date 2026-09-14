package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import vikoba.service.loan.entity.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @Query("select distinct l.groupMember.group.id from Loan l where l.status = 'ACTIVE'")
    java.util.List<Long> findActiveLoanGroupIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findLockedById(@Param("id") Long id);
    @Query("""
                    SELECT l FROM Loan l
                    WHERE l.groupMember.id = :groupMemberId
                    ORDER BY l.applicationDate DESC
            """)
    java.util.List<Loan> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);

    @Query("select l from Loan l join fetch l.groupMember gm join fetch gm.member where gm.group.id = :groupId order by l.applicationDate desc")
    java.util.List<Loan> findByGroupId(@Param("groupId") Long groupId);
    @Query("select l from Loan l where l.groupMember.id = :memberId and l.status in ('PENDING','UNDER_REVIEW','APPROVED','DISBURSED','ACTIVE','DEFAULTED')")
    java.util.List<Loan> findOpenByGroupMemberId(@Param("memberId") Long memberId);
}
