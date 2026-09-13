package vikoba.service.loan.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanApprovalEvent;
import java.util.List;
public interface LoanApprovalEventRepository extends JpaRepository<LoanApprovalEvent, Long> {
    List<LoanApprovalEvent> findByLoanIdOrderByActedAtAscIdAsc(Long loanId);
}
