package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {
}
