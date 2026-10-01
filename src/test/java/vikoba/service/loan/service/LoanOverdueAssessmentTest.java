package vikoba.service.loan.service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import vikoba.service.common.enums.*;
import vikoba.service.loan.entity.*;
import vikoba.service.loan.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanOverdueAssessmentTest {
    @Mock LoanInstallmentRepository installments;
    @Mock LoanRepository loans;
    @Mock LoanApprovalStepRepository approvalSteps;
    @Mock LoanApprovalEventRepository approvalEvents;
    @Mock LoanGuarantorRepository guarantors;
    @Mock vikoba.service.organization.service.GroupAuthorizationService authorizationService;
    @InjectMocks LoanWorkflowService service;
    final LocalDate today = LocalDate.of(2026, 10, 1);
    private Loan loan(LoanStatus status) {
        Loan loan = new Loan(); loan.setId(7L); loan.setStatus(status);
        loan.setLateFineAtApplication(new BigDecimal("2000"));
        return loan;
    }
    private LoanInstallment installment(Loan loan, LocalDate due, String paid) {
        LoanInstallment row = LoanInstallment.builder().loan(loan).dueDate(due)
            .principalAmount(new BigDecimal("9000")).interestAmount(new BigDecimal("1000"))
            .totalAmount(new BigDecimal("10000")).paidAmount(new BigDecimal(paid)).build();
        row.setId(12L); return row;
    }
    @Test void latePartialPaymentGetsOnePersistedFineIncludedInBalance() {
        Loan loan = loan(LoanStatus.ACTIVE);
        var row = installment(loan, today.minusDays(1), "3000");
        when(installments.findByLoanIdOrderByInstallmentNumberAsc(7L)).thenReturn(List.of(row));
        assertEquals(1, service.assessLoanOverdue(loan, today));
        assertEquals(new BigDecimal("2000"), row.getPenaltyAmount());
        assertEquals(new BigDecimal("9000"), row.getTotalAmount().subtract(row.getPaidAmount()));
        assertEquals(InstallmentStatus.OVERDUE, row.getStatus());
        verify(installments).save(row);
        assertEquals(0, service.assessLoanOverdue(loan, today.plusDays(2)));
        assertEquals(new BigDecimal("12000"), row.getTotalAmount());
        row.setPaidAmount(new BigDecimal("12000")); row.setStatus(InstallmentStatus.PAID);
        assertEquals(0, service.assessLoanOverdue(loan, today.plusDays(3)));
        assertEquals(InstallmentStatus.PAID, row.getStatus());
    }
    @Test void paidTodayAndFutureInstallmentsAreNotFined() {
        Loan loan = loan(LoanStatus.ACTIVE);
        var paid = installment(loan, today.minusDays(1), "10000");
        var dueToday = installment(loan, today, "0");
        var future = installment(loan, today.plusDays(1), "0");
        when(installments.findByLoanIdOrderByInstallmentNumberAsc(7L)).thenReturn(List.of(paid, dueToday, future));
        assertEquals(0, service.assessLoanOverdue(loan, today));
        verify(installments, never()).save(any());
    }
    @Test void defaultedLoansAreAssessedAndZeroFineRemainsZero() {
        Loan loan = loan(LoanStatus.DEFAULTED);
        var row = installment(loan, today.minusDays(1), "0");
        when(installments.findByLoanIdOrderByInstallmentNumberAsc(7L)).thenReturn(List.of(row));
        loan.setLateFineAtApplication(BigDecimal.ZERO);
        assertEquals(0, service.assessLoanOverdue(loan, today));
        assertEquals(InstallmentStatus.OVERDUE, row.getStatus());
        assertEquals(BigDecimal.ZERO, row.getPenaltyAmount());
    }

    @Test void repaymentCannotCompleteLoanUntilPersistedLateFineIsPaid() {
        Loan loan = loan(LoanStatus.ACTIVE);
        var group = new vikoba.service.organization.entity.VikobaGroup(); group.setId(1L);
        var person = new vikoba.service.organization.entity.Member(); person.setFirstName("Asha"); person.setLastName("Member");
        var member = new vikoba.service.organization.entity.GroupMember(); member.setId(42L); member.setGroup(group); member.setMember(person);
        loan.setGroupMember(member); loan.setLoanProduct(new LoanProduct());
        loan.setPrincipalAmount(new BigDecimal("9000")); loan.setInterestAmount(new BigDecimal("1000")); loan.setTotalAmount(new BigDecimal("10000"));
        var row = installment(loan, LocalDate.now(java.time.ZoneId.of("Africa/Dar_es_Salaam")).minusDays(1), "0");
        when(loans.findLockedById(7L)).thenReturn(java.util.Optional.of(loan));
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(member);
        when(installments.findByLoanIdOrderByInstallmentNumberAsc(7L)).thenReturn(List.of(row));
        var payment = new vikoba.service.loan.dto.LoanRepaymentRequest(); payment.setAmount(new BigDecimal("10000"));
        var result = service.repay(1L, 7L, payment);
        assertEquals(new BigDecimal("2000"), result.getRemainingBalance());
        assertEquals(InstallmentStatus.OVERDUE, row.getStatus());
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        payment.setAmount(new BigDecimal("2000"));
        result = service.repay(1L, 7L, payment);
        assertEquals(0, result.getRemainingBalance().signum());
        assertEquals(InstallmentStatus.PAID, row.getStatus());
        assertEquals(LoanStatus.COMPLETED, loan.getStatus());
        verify(authorizationService, times(2)).requirePermission(1L, "LOAN_MANAGE");
    }
}
