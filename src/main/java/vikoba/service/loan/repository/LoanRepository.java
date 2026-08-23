package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
