package vikoba.service.loan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.*;
import vikoba.service.contribution.entity.Payment;
import vikoba.service.contribution.repository.MemberContributionRepository;
import vikoba.service.contribution.repository.PaymentRepository;
import vikoba.service.contribution.repository.ShareTransactionRepository;
import vikoba.service.fine.entity.*;
import vikoba.service.fine.repository.*;
import vikoba.service.loan.dto.*;
import vikoba.service.loan.entity.*;
import vikoba.service.loan.repository.*;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import vikoba.service.notification.SmsNotificationService;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private final LoanRepository loans;
    private final LoanProductRepository products;
    private final LoanInstallmentRepository installments;
    private final GroupMemberRepository members;
    private final GroupSettingsRepository settings;
    private final MemberContributionRepository contributions;
    private final ShareTransactionRepository shareTransactions;
    private final PaymentRepository payments;
    private final FineRepository fines;
    private final FineTypeRepository fineTypes;
    private final GroupSettingsRepository groupSettingsRepository;
    private final SmsNotificationService smsNotificationService;

    @Transactional(readOnly = true)
    public List<LoanResponse> list(Long groupId) {
        return loans.findByGroupId(groupId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> products(Long groupId) {
        return products.findByGroupIdAndActiveTrueOrderByNameAsc(groupId).stream().map(this::productResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanInstallmentResponse> schedule(Long groupId, Long loanId) {
        return schedule(require(loanId, groupId));
    }

    @Transactional
    public LoanResponse apply(Long groupId, LoanRequest r) {

        // 1. Validate member
        GroupMember member = members.findById(r.getGroupMemberId())
                .filter(m -> m.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Applicant is not a member of this group."));

        // 2. Prevent multiple open loans
        if (!loans.findOpenByGroupMemberId(member.getId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "The member already has an open loan application or loan.");
        }

        // 3. Get group loan settings
        GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configure loan settings before accepting applications."));

        // 4. Requested amount
        BigDecimal amount = positive(
                r.getPrincipalAmount(),
                "principal amount");

        // 5. Calculate member contribution value
        BigDecimal contributionsValue = contributions.findByGroupMemberId(member.getId())
                .stream()
                .map(c -> c.getPaidAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Calculate member share value
        BigDecimal sharesValue = shareTransactions.findLedgerByGroupId(groupId)
                .stream()
                .filter(st -> st.getGroupMember().getId().equals(member.getId()))
                .map(st -> {

                    BigDecimal value = st.getUnitPrice()
                            .multiply(
                                    BigDecimal.valueOf(
                                            st.getQuantity()));

                    return switch (st.getType()) {
                        case TRANSFER_OUT, REDEMPTION ->
                            value.negate();

                        default ->
                            value;
                    };
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 7. Determine qualifying base
        BigDecimal eligibilityBase = contributionsValue.max(sharesValue);

        // 8. Get multiplier
        BigDecimal multiplier = orZero(settings.getLoanMultiplier());

        if (multiplier.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Loan multiplier has not been configured for this group.");
        }

        // 9. Calculate maximum loan
        BigDecimal maximumLoan = eligibilityBase.multiply(multiplier);

        if (maximumLoan.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Applicant has no qualifying contributions or shares for a loan.");
        }

        // 10. Validate requested amount
        if (amount.compareTo(maximumLoan) > 0) {
            throw new IllegalArgumentException(
                    "Requested amount exceeds the member's loan eligibility limit of "
                            + maximumLoan
                            + ". Based on qualifying value of "
                            + eligibilityBase
                            + " × loan multiplier of "
                            + multiplier
                            + ".");
        }

        // IMPORTANT:
        // Do NOT validate against LoanProduct.minimumAmount.
        // Loan eligibility is controlled by the group multiplier.

        // 11. Repayment period
        int months = r.getDurationMonths() == null
                ? settings.getDefaultLoanDurationMonths()
                : r.getDurationMonths();

        if (months <= 0) {
            throw new IllegalArgumentException(
                    "Select a valid repayment period.");
        }

        // 12. Resolve product only if your system still needs
        // a product for interest configuration.
        LoanProduct product = resolveProduct(groupId, r, settings);

        if (product == null) {
            throw new IllegalArgumentException(
                    "No loan configuration is available for this group.");
        }

        // 13. Validate maximum duration from settings/product
        if (product.getMaxDurationMonths() != null
                && months > product.getMaxDurationMonths()) {

            throw new IllegalArgumentException(
                    "Maximum repayment period is "
                            + product.getMaxDurationMonths()
                            + " months.");
        }

        // 14. Calculate interest
        BigDecimal interest = amount
                .multiply(product.getInterestRate())
                .multiply(BigDecimal.valueOf(months))
                .divide(
                        BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP);

        // 15. Create loan
        Loan loan = loans.save(
                Loan.builder()
                        .groupMember(member)
                        .loanProduct(product)
                        .loanNumber(
                                "LN-" +
                                        UUID.randomUUID()
                                                .toString()
                                                .replace("-", "")
                                                .substring(0, 10)
                                                .toUpperCase())
                        .principalAmount(amount)
                        .interestAmount(interest)
                        .totalAmount(amount.add(interest))
                        .durationMonths(months)
                        .applicationDate(LocalDate.now())
                        .status(LoanStatus.PENDING)
                        .purpose(required(
                                r.getPurpose(),
                                "purpose"))
                        .build());

        return response(loan);
    }

    @Transactional
    public LoanResponse approve(Long groupId, Long id) {
        Loan l = require(id, groupId);
        if (l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.UNDER_REVIEW)
            throw new IllegalArgumentException("Only pending applications can be approved.");
        l.setStatus(LoanStatus.APPROVED);
        l.setApprovalDate(LocalDate.now());
        return response(l);
    }

    @Transactional
    public LoanResponse reject(Long groupId, Long id, LoanDecisionRequest r) {
        Loan l = require(id, groupId);
        if (l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.UNDER_REVIEW)
            throw new IllegalArgumentException("Only pending applications can be rejected.");
        l.setStatus(LoanStatus.REJECTED);
        l.setRejectionReason(required(r.getRejectionReason(), "rejection reason"));
        return response(l);
    }

    @Transactional
    public LoanResponse disburse(Long groupId, Long id) {
        Loan l = require(id, groupId);
        if (l.getStatus() != LoanStatus.APPROVED)
            throw new IllegalArgumentException("Approve the application before disbursement.");
        l.setStatus(LoanStatus.ACTIVE);
        l.setDisbursementDate(LocalDate.now());
        l.setMaturityDate(LocalDate.now().plusMonths(l.getDurationMonths()));
        createSchedule(l);
        smsNotificationService.send(l.getGroupMember().getMember().getPhone(), "VIKOBA360: Hongera! Mkopo "
                + l.getLoanNumber() + " umetolewa kwa TZS " + l.getPrincipalAmount().toPlainString()
                + ". Angalia ratiba ya marejesho kwenye akaunti yako.");
        return response(l);
    }

    @Transactional
    public LoanResponse repay(Long groupId, Long id, LoanRepaymentRequest r) {
        Loan l = require(id, groupId);
        if (l.getStatus() != LoanStatus.ACTIVE && l.getStatus() != LoanStatus.DEFAULTED)
            throw new IllegalArgumentException("This loan is not open for repayment.");
        BigDecimal left = positive(r.getAmount(), "repayment amount");
        for (LoanInstallment i : installments.findByLoanIdOrderByInstallmentNumberAsc(id)) {
            if (left.signum() <= 0)
                break;
            BigDecimal due = i.getTotalAmount().subtract(i.getPaidAmount()), paid = left.min(due);
            i.setPaidAmount(i.getPaidAmount().add(paid));
            left = left.subtract(paid);
            i.setStatus(i.getPaidAmount().compareTo(i.getTotalAmount()) >= 0 ? InstallmentStatus.PAID
                    : InstallmentStatus.PARTIAL);
        }
        if (left.signum() > 0)
            throw new IllegalArgumentException("Repayment exceeds the outstanding loan balance.");
        if (schedule(l).stream().allMatch(i -> i.getBalance().signum() == 0))
            l.setStatus(LoanStatus.COMPLETED);
        return response(l);
    }

    @Transactional
    public int assessOverdue(Long groupId) {
        GroupSettings s = settings.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Loan settings not found."));
        BigDecimal fine = orZero(s.getLatePaymentFine());
        int count = 0;
        for (Loan l : loans.findByGroupId(groupId)) {
            if (l.getStatus() != LoanStatus.ACTIVE)
                continue;
            for (LoanInstallment i : installments.findByLoanIdOrderByInstallmentNumberAsc(l.getId()))
                if (i.getDueDate().isBefore(LocalDate.now()) && i.getPaidAmount().compareTo(i.getTotalAmount()) < 0) {
                    i.setStatus(InstallmentStatus.OVERDUE);
                    if (fine.signum() > 0 && i.getPenaltyAmount().signum() == 0) {
                        i.setPenaltyAmount(fine);
                        i.setTotalAmount(i.getTotalAmount().add(fine));
                        count++;
                    }
                }
        }
        return count;
    }

    private void createSchedule(Loan l) {
        BigDecimal principal = l.getPrincipalAmount().divide(BigDecimal.valueOf(l.getDurationMonths()), 2,
                RoundingMode.HALF_UP),
                interest = l.getInterestAmount().divide(BigDecimal.valueOf(l.getDurationMonths()), 2,
                        RoundingMode.HALF_UP);
        for (int n = 1; n <= l.getDurationMonths(); n++) {
            BigDecimal p = n == l.getDurationMonths()
                    ? l.getPrincipalAmount().subtract(principal.multiply(BigDecimal.valueOf(n - 1)))
                    : principal,
                    in = n == l.getDurationMonths()
                            ? l.getInterestAmount().subtract(interest.multiply(BigDecimal.valueOf(n - 1)))
                            : interest;
            installments.save(LoanInstallment.builder().loan(l).installmentNumber(n)
                    .dueDate(l.getDisbursementDate().plusMonths(n)).principalAmount(p).interestAmount(in)
                    .totalAmount(p.add(in)).build());
        }
    }

    private LoanProduct resolveProduct(Long gid, LoanRequest r, GroupSettings s) {
        if (r.getLoanProductId() != null)
            return products.findByIdAndGroupId(r.getLoanProductId(), gid)
                    .orElseThrow(() -> new IllegalArgumentException("Loan product not found."));
        return products.findByGroupIdAndActiveTrueOrderByNameAsc(gid).stream().findFirst()
                .orElseGet(() -> products.save(
                        LoanProduct.builder().group(members.findById(r.getGroupMemberId()).orElseThrow().getGroup())
                                .code("STANDARD").name("Standard Group Loan").minimumAmount(BigDecimal.ONE)
                                .maximumAmount(new BigDecimal("999999999"))
                                .interestRate(orZero(s.getDefaultInterestRate())).interestType(InterestType.FLAT)
                                .maxDurationMonths(s.getDefaultLoanDurationMonths()).active(true).build()));
    }

    private Loan require(Long id, Long gid) {
        return loans.findById(id).filter(l -> l.getGroupMember().getGroup().getId().equals(gid))
                .orElseThrow(() -> new IllegalArgumentException("Loan not found in this group."));
    }

    private List<LoanInstallmentResponse> schedule(Loan l) {
        return installments.findByLoanIdOrderByInstallmentNumberAsc(l.getId()).stream()
                .map(i -> LoanInstallmentResponse.builder().id(i.getId()).installmentNumber(i.getInstallmentNumber())
                        .dueDate(i.getDueDate()).principalAmount(i.getPrincipalAmount())
                        .interestAmount(i.getInterestAmount()).penaltyAmount(i.getPenaltyAmount())
                        .totalAmount(i.getTotalAmount()).paidAmount(i.getPaidAmount())
                        .balance(i.getTotalAmount().subtract(i.getPaidAmount())).status(i.getStatus().name()).build())
                .toList();
    }

    private LoanProductResponse productResponse(LoanProduct p) {
        return LoanProductResponse.builder().id(p.getId()).code(p.getCode()).name(p.getName())
                .description(p.getDescription()).minimumAmount(p.getMinimumAmount()).maximumAmount(p.getMaximumAmount())
                .interestRate(p.getInterestRate()).interestType(p.getInterestType().name())
                .maxDurationMonths(p.getMaxDurationMonths()).active(p.isActive()).build();
    }

    private LoanResponse response(Loan l) {
        List<LoanInstallmentResponse> s = schedule(l);
        BigDecimal paid = s.stream().map(LoanInstallmentResponse::getPaidAmount).reduce(BigDecimal.ZERO,
                BigDecimal::add),
                total = s.isEmpty() ? l.getTotalAmount()
                        : s.stream().map(LoanInstallmentResponse::getTotalAmount).reduce(BigDecimal.ZERO,
                                BigDecimal::add),
                balance = total.subtract(paid);
        return LoanResponse.builder().id(l.getId()).groupMemberId(l.getGroupMember().getId())
                .memberName(l.getGroupMember().getMember().getFirstName() + " "
                        + l.getGroupMember().getMember().getLastName())
                .membershipNumber(l.getGroupMember().getMembershipNumber()).loanProductId(l.getLoanProduct().getId())
                .loanProductName(l.getLoanProduct().getName()).interestRate(l.getLoanProduct().getInterestRate())
                .loanNumber(l.getLoanNumber()).principalAmount(l.getPrincipalAmount())
                .interestAmount(l.getInterestAmount()).totalAmount(total).durationMonths(l.getDurationMonths())
                .applicationDate(l.getApplicationDate()).approvalDate(l.getApprovalDate())
                .disbursementDate(l.getDisbursementDate()).maturityDate(l.getMaturityDate())
                .status(l.getStatus().name()).purpose(l.getPurpose()).rejectionReason(l.getRejectionReason())
                .totalPaid(paid).remainingBalance(balance)
                .progress(total.signum() == 0 ? 0
                        : paid.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.DOWN).intValue())
                .build();
    }

    private BigDecimal positive(BigDecimal n, String f) {
        if (n == null || n.signum() <= 0)
            throw new IllegalArgumentException(f + " must be greater than zero.");
        return n;
    }

    private BigDecimal orZero(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    private String required(String v, String f) {
        if (v == null || v.isBlank())
            throw new IllegalArgumentException(f + " is required.");
        return v.trim();
    }
}
