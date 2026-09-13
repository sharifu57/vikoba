package vikoba.service.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.loan.entity.LoanGuarantor;
import vikoba.service.common.enums.GuarantorStatus;

import java.util.Collection;
import java.util.List;

public interface LoanGuarantorRepository extends JpaRepository<LoanGuarantor, Long> {
    List<LoanGuarantor> findByGroupMemberIdAndLoanStatusIn(Long groupMemberId, Collection<LoanStatus> statuses);
    List<LoanGuarantor> findByLoanId(Long loanId);
    List<LoanGuarantor> findByGroupMemberIdAndStatus(Long groupMemberId, GuarantorStatus status);
}
