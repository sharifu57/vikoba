package vikoba.service.loan.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanApprovalStep;
import java.util.List;
public interface LoanApprovalStepRepository extends JpaRepository<LoanApprovalStep, Long> {
    List<LoanApprovalStep> findByLoanIdOrderByStepOrderAsc(Long loanId);
}
