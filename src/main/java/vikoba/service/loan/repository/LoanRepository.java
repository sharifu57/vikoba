package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import vikoba.service.loan.entity.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @Query("""
                    SELECT l FROM Loan l
                    WHERE l.groupMember.id = :groupMemberId
                    ORDER BY l.applicationDate DESC
            """)
    java.util.List<Loan> findByGroupMemberId(@Param("groupMemberId") Long groupMemberId);

    @Query("select l from Loan l join fetch l.groupMember gm join fetch gm.member where gm.group.id = :groupId order by l.applicationDate desc")
    java.util.List<Loan> findByGroupId(@Param("groupId") Long groupId);
    @Query("select l from Loan l where l.groupMember.id = :memberId and l.status in ('PENDING','UNDER_REVIEW','APPROVED','DISBURSED','ACTIVE')")
    java.util.List<Loan> findOpenByGroupMemberId(@Param("memberId") Long memberId);
}
