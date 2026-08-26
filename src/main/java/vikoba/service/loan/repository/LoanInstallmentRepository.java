package vikoba.service.loan.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.loan.entity.LoanInstallment;
import java.util.*;
public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> { List<LoanInstallment> findByLoanIdOrderByInstallmentNumberAsc(Long loanId); }
