package vikoba.service.loan.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vikoba.service.common.enums.LoanStatus;
import vikoba.service.loan.dto.LoanRequest;
import vikoba.service.loan.entity.Loan;
import vikoba.service.loan.entity.LoanApprovalStep;
import vikoba.service.loan.repository.LoanInstallmentRepository;
import vikoba.service.accounting.service.AccountingService;
import vikoba.service.accounting.dto.AccountResponse;
import vikoba.service.notification.SmsNotificationService;
import vikoba.service.loan.repository.LoanApprovalStepRepository;
import vikoba.service.loan.repository.LoanApprovalEventRepository;
import vikoba.service.common.enums.GroupRole;
import org.springframework.security.access.AccessDeniedException;
import vikoba.service.loan.repository.LoanRepository;
import vikoba.service.loan.repository.LoanProductRepository;
import vikoba.service.loan.repository.LoanGuarantorRepository;
import vikoba.service.loan.entity.LoanProduct;
import vikoba.service.contribution.repository.ShareTransactionRepository;
import vikoba.service.contribution.entity.ShareTransaction;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.entity.Member;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.service.GroupAuthorizationService;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanWorkflowServiceTest {
    @Mock LoanRepository loans;
    @Mock LoanApprovalStepRepository approvalSteps;
    @Mock LoanApprovalEventRepository approvalEvents;
    @Mock LoanInstallmentRepository installments;
    @Mock AccountingService accountingService;
    @Mock SmsNotificationService smsNotificationService;
    @Mock LoanProductRepository products;
    @Mock ShareTransactionRepository shares;
    @Mock GroupSettingsRepository settings;
    @Mock GroupMemberRepository members;
    @Mock LoanGuarantorRepository guarantors;
    @Mock GroupAuthorizationService authorizationService;
    @InjectMocks LoanWorkflowService service;

    @Test
    void applicantCannotSubmitForAnotherMember() {
        GroupMember self = new GroupMember();
        self.setId(10L);
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(self);
        LoanRequest request = new LoanRequest();
        request.setGroupMemberId(11L);
        request.setConsentAccepted(true);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.apply(1L, request));
    }

    @Test
    void managerCannotApproveBeforeGuarantorsAccept() {
        VikobaGroup group = new VikobaGroup();
        group.setId(1L);
        GroupMember borrower = new GroupMember();
        borrower.setGroup(group);
        Loan pending = new Loan();
        pending.setGroupMember(borrower);
        pending.setStatus(LoanStatus.PENDING);
        when(loans.findLockedById(3L)).thenReturn(Optional.of(pending));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.approve(1L, 3L));
        assertTrue(error.getMessage().contains("guarantors"));

    }

    @Test
    void accountantCannotApproveBeforeChairStep() {
        VikobaGroup group = new VikobaGroup();
        group.setId(1L);
        GroupMember borrower = new GroupMember();
        borrower.setId(11L);
        borrower.setGroup(group);
        GroupMember accountant = new GroupMember();
        accountant.setId(12L);
        Loan loan = new Loan();
        loan.setId(3L);
        loan.setGroupMember(borrower);
        loan.setStatus(LoanStatus.UNDER_REVIEW);
        LoanApprovalStep chair = LoanApprovalStep.builder().loan(loan).stepOrder(1)
                .requiredRole(GroupRole.GROUP_CHAIRMAN).label("Chair review").build();
        LoanApprovalStep accounting = LoanApprovalStep.builder().loan(loan).stepOrder(2)
                .requiredRole(GroupRole.ACCOUNTANT).label("Accountant review").build();
        when(loans.findLockedById(3L)).thenReturn(Optional.of(loan));
        when(guarantors.findByLoanId(3L)).thenReturn(List.of());
        when(approvalSteps.findByLoanIdOrderByStepOrderAsc(3L)).thenReturn(List.of(chair, accounting));
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(accountant);

        assertThrows(AccessDeniedException.class, () -> service.approve(1L, 3L));
        assertEquals(null, chair.getApprovedAt());
    }

    @Test
    void finalAccountantApprovalActivatesLoanAndPostsDisbursement() {
        VikobaGroup group = new VikobaGroup();
        group.setId(1L);
        group.setEndDate(LocalDate.now().plusMonths(6));
        GroupMember borrower = new GroupMember();
        borrower.setId(11L);
        borrower.setGroup(group);
        borrower.setMembershipNumber("M-11");
        Member profile = new Member();
        profile.setFirstName("Asha");
        profile.setLastName("Juma");
        profile.setPhone("255700000000");
        borrower.setMember(profile);
        GroupMember accountant = new GroupMember();
        accountant.setId(12L);
        LoanProduct product = new LoanProduct();
        product.setId(8L);
        product.setName("Standard");
        product.setInterestRate(BigDecimal.TEN);
        Loan loan = Loan.builder().groupMember(borrower).loanProduct(product).loanNumber("LN-TEST-1")
                .principalAmount(new BigDecimal("10000")).interestAmount(new BigDecimal("1000"))
                .totalAmount(new BigDecimal("11000")).durationMonths(1).status(LoanStatus.UNDER_REVIEW)
                .applicationDate(LocalDate.now()).build();
        loan.setId(3L);
        loan.setRequiredGuarantorsAtApplication(0);
        loan.setLateFineAtApplication(new BigDecimal("3000"));
        LoanApprovalStep chair = LoanApprovalStep.builder().loan(loan).stepOrder(1)
                .requiredRole(GroupRole.GROUP_CHAIRMAN).label("Chair review")
                .approvedAt(java.time.LocalDateTime.now()).approvedByMemberId(13L).build();
        LoanApprovalStep accounting = LoanApprovalStep.builder().loan(loan).stepOrder(2)
                .requiredRole(GroupRole.ACCOUNTANT).label("Accountant review").build();
        when(loans.findLockedById(3L)).thenReturn(Optional.of(loan));
        when(guarantors.findByLoanId(3L)).thenReturn(List.of());
        when(approvalSteps.findByLoanIdOrderByStepOrderAsc(3L)).thenReturn(List.of(chair, accounting));
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(accountant);
        when(authorizationService.hasRole(1L, GroupRole.ACCOUNTANT)).thenReturn(true);
        when(accountingService.ensureDefaultAccountsForGroup(1L)).thenReturn(List.of(
                AccountResponse.builder().id(1L).code("1000").build(),
                AccountResponse.builder().id(2L).code("1100").build()));

        var result = service.approve(1L, 3L);
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(new BigDecimal("3000"), result.getLatePaymentFine());
        assertEquals(LocalDate.now().plusMonths(1), loan.getMaturityDate());
        verify(accountingService).post(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(installments).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void borrowingLimitUsesShareValueTimesGroupMultiplier() {
        GroupMember self = new GroupMember();
        self.setId(10L);
        VikobaGroup group = new VikobaGroup();
        group.setId(1L);
        group.setEndDate(LocalDate.now().plusMonths(8));
        self.setGroup(group);
        self.setMembershipNumber("M-10");
        Member profile = new Member();
        profile.setFirstName("Asha");
        profile.setLastName("Juma");
        self.setMember(profile);
        GroupSettings rule = new GroupSettings();
        rule.setLoanMultiplier(new BigDecimal("3"));
        rule.setDefaultInterestRate(new BigDecimal("8"));
        rule.setDefaultLoanDurationMonths(6);
        rule.setRequiredLoanGuarantors(0);
        ShareTransaction purchase = new ShareTransaction();
        purchase.setGroupMember(self);
        purchase.setUnitPrice(new BigDecimal("5000"));
        purchase.setQuantity(new BigDecimal("2"));
        purchase.setType(ShareTransactionType.PURCHASE);
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(self);
        when(settings.findByGroupId(1L)).thenReturn(Optional.of(rule));
        when(shares.findLedgerByGroupId(1L)).thenReturn(List.of(purchase));
        when(products.findByGroupIdAndActiveTrueOrderByNameAsc(1L)).thenReturn(List.of());
        when(members.findByGroupIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(List.of());

        var context = service.applicationContext(1L);
        assertEquals(new BigDecimal("10000"), context.sharesValue());
        assertEquals(new BigDecimal("30000"), context.maximumLoan());
        assertEquals("Asha Juma", context.name());
    }

    @Test
    void repaymentLimitRespectsSettingsProductAndKikobaEndDate() {
        GroupSettings rule = new GroupSettings();
        rule.setDefaultLoanDurationMonths(12);
        LoanProduct product = new LoanProduct();
        product.setMaxDurationMonths(10);
        LocalDate today = LocalDate.of(2026, 9, 13);

        assertEquals(3, LoanWorkflowService.maximumRepaymentMonths(rule, product,
                LocalDate.of(2026, 12, 13), today));
        assertEquals(10, LoanWorkflowService.maximumRepaymentMonths(rule, product,
                LocalDate.of(2027, 12, 13), today));
        rule.setDefaultLoanDurationMonths(2);
        assertEquals(2, LoanWorkflowService.maximumRepaymentMonths(rule, product,
                LocalDate.of(2027, 12, 13), today));
        assertEquals(0, LoanWorkflowService.maximumRepaymentMonths(rule, product,
                LocalDate.of(2026, 10, 12), today));
    }

    @Test
    void applicantCannotGuaranteeOwnLoan() {
        VikobaGroup group = new VikobaGroup();
        group.setId(1L);
        GroupMember self = new GroupMember();
        self.setId(10L);
        self.setGroup(group);
        self.setStatus(MembershipStatus.ACTIVE);
        GroupSettings rule = new GroupSettings();
        rule.setRequiredLoanGuarantors(1);
        when(authorizationService.requireCurrentMembership(1L)).thenReturn(self);
        when(settings.findByGroupId(1L)).thenReturn(Optional.of(rule));
        when(members.findByIdForUpdate(10L)).thenReturn(Optional.of(self));
        LoanRequest request = new LoanRequest();
        request.setConsentAccepted(true);
        request.setGuarantorIds(List.of(10L));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.apply(1L, request));
        assertTrue(error.getMessage().contains("own loan"));
    }
}
