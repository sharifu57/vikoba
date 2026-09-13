package vikoba.service.loan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.*;
import vikoba.service.contribution.entity.Payment;
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
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.LoanApprovalWorkflowService;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private final LoanRepository loans;
    private final LoanProductRepository products;
    private final LoanInstallmentRepository installments;
    private final GroupMemberRepository members;
    private final GroupSettingsRepository settings;
    private final ShareTransactionRepository shareTransactions;
    private final PaymentRepository payments;
    private final FineRepository fines;
    private final FineTypeRepository fineTypes;
    private final LoanGuarantorRepository guarantors;
    private final GroupSettingsRepository groupSettingsRepository;
    private final SmsNotificationService smsNotificationService;
    private final GroupAuthorizationService authorizationService;
    private final LoanApprovalWorkflowService loanApprovalWorkflow;
    private final LoanApprovalStepRepository approvalSteps;
    private final LoanApprovalEventRepository approvalEvents;

    private static final List<LoanStatus> OPEN_STATUSES = List.of(LoanStatus.PENDING, LoanStatus.UNDER_REVIEW,
            LoanStatus.APPROVED, LoanStatus.DISBURSED, LoanStatus.ACTIVE, LoanStatus.DEFAULTED);

    @Transactional(readOnly = true)
    public LoanApplicationContext applicationContext(Long groupId) {
        GroupMember self = authorizationService.requireCurrentMembership(groupId);
        GroupSettings setting = settings.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Configure loan settings before applying."));
        BigDecimal sharesValue = shareValue(groupId, self.getId());
        BigDecimal multiplier = orZero(setting.getLoanMultiplier());
        var product = products.findByGroupIdAndActiveTrueOrderByNameAsc(groupId).stream().findFirst().orElse(null);
        var profile = self.getMember();
        int maxMonths = maximumRepaymentMonths(setting, product, self.getGroup().getEndDate(), LocalDate.now());
        List<LoanGuarantorOption> candidates = members.findByGroupIdAndStatus(groupId, MembershipStatus.ACTIVE).stream()
                .filter(candidate -> !candidate.getId().equals(self.getId()))
                .map(candidate -> {
                    String reason = !loans.findOpenByGroupMemberId(candidate.getId()).isEmpty() ? "Has an open loan"
                            : guarantorCommitted(candidate.getId()) ? "Guaranteeing another open loan" : null;
                    var person = candidate.getMember();
                    return new LoanGuarantorOption(candidate.getId(), person.getFirstName() + " " + person.getLastName(),
                            person.getPhone(), person.getAddress(), candidate.getMembershipNumber(), null, reason == null, reason);
                }).toList();
        return new LoanApplicationContext(self.getId(), profile.getFirstName() + " " + profile.getLastName(),
                profile.getNationalId(), profile.getPhone(), profile.getAddress(), self.getMembershipNumber(),
                sharesValue, multiplier, sharesValue.multiply(multiplier), setting.getRequiredLoanGuarantors(),
                setting.getDefaultLoanDurationMonths(), maxMonths,
                product == null ? orZero(setting.getDefaultInterestRate()) : product.getInterestRate(),
                self.getGroup().getEndDate(), candidates);
    }

    static int maximumRepaymentMonths(GroupSettings setting, LoanProduct product, LocalDate groupEndDate, LocalDate today) {
        if (groupEndDate == null || !groupEndDate.isAfter(today)) return 0;
        int configured = setting.getDefaultLoanDurationMonths() == null ? 0 : setting.getDefaultLoanDurationMonths();
        if (configured <= 0) return 0;
        int productLimit = product == null || product.getMaxDurationMonths() == null
                ? configured : product.getMaxDurationMonths();
        int max = Math.min(configured, productLimit);
        int allowed = 0;
        while (allowed < max && !today.plusMonths(allowed + 1L).isAfter(groupEndDate)) allowed++;
        return allowed;
    }

    private BigDecimal shareValue(Long groupId, Long memberId) {
        return shareTransactions.findLedgerByGroupId(groupId).stream()
                .filter(transaction -> transaction.getGroupMember().getId().equals(memberId))
                .map(transaction -> transaction.getUnitPrice().multiply(transaction.getQuantity())
                        .multiply(transaction.getType() == ShareTransactionType.TRANSFER_OUT || transaction.getType() == ShareTransactionType.REDEMPTION
                                ? BigDecimal.ONE.negate() : BigDecimal.ONE))
                .reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
    }

    private boolean guarantorCommitted(Long memberId) {
        return guarantors.findByGroupMemberIdAndLoanStatusIn(memberId, OPEN_STATUSES).stream()
                .anyMatch(guarantor -> guarantor.getStatus() == GuarantorStatus.PENDING || guarantor.getStatus() == GuarantorStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> list(Long groupId) {
        authorizationService.requireMembership(groupId);
        return loans.findByGroupId(groupId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> products(Long groupId) {
        authorizationService.requireMembership(groupId);
        return products.findByGroupIdAndActiveTrueOrderByNameAsc(groupId).stream().map(this::productResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LoanInstallmentResponse> schedule(Long groupId, Long loanId) {
        return schedule(require(loanId, groupId));
    }

    @Transactional
    public LoanResponse apply(Long groupId, LoanRequest r) {
        GroupMember signedInMember = authorizationService.requireCurrentMembership(groupId);
        if (r.getGroupMemberId() != null && !r.getGroupMemberId().equals(signedInMember.getId()))
            throw new AccessDeniedException("You can only apply for your own loan.");
        if (!Boolean.TRUE.equals(r.getConsentAccepted()))
            throw new IllegalArgumentException("Accept the loan terms before submitting your application.");

        // 1. Validate member
        GroupMember member = signedInMember;
        r.setGroupMemberId(member.getId());

        // 2. Prevent multiple open loans
        if (!loans.findOpenByGroupMemberId(member.getId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "The member already has an open loan application or loan.");
        }
        if (guarantorCommitted(member.getId())) {
            throw new IllegalArgumentException("You cannot apply while guaranteeing another open loan.");
        }

        // 3. Get group loan settings
        GroupSettings settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configure loan settings before accepting applications."));

        validateGuarantors(groupId, member, r.getGuarantorIds(), settings.getRequiredLoanGuarantors());

        // 4. Requested amount
        BigDecimal amount = positive(
                r.getPrincipalAmount(),
                "principal amount");

        // 5. Calculate member contribution value
        BigDecimal sharesValue = shareValue(groupId, member.getId());

        // 7. Determine qualifying base
        BigDecimal eligibilityBase = sharesValue.max(BigDecimal.ZERO);

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
        int permittedMonths = maximumRepaymentMonths(settings, product, member.getGroup().getEndDate(), LocalDate.now());
        if (permittedMonths <= 0)
            throw new IllegalArgumentException("No repayment period fits before the Kikoba end date. Ask your group admin to review the group cycle.");
        if (months > permittedMonths)
            throw new IllegalArgumentException("Repayment period cannot exceed " + permittedMonths
                    + " month(s), based on group loan settings and the Kikoba end date of "
                    + member.getGroup().getEndDate() + ".");

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
                        .status((settings.getRequiredLoanGuarantors() == null || settings.getRequiredLoanGuarantors() == 0)
                                ? LoanStatus.UNDER_REVIEW : LoanStatus.PENDING)
                        .purpose(required(
                                r.getPurpose(),
                                "purpose"))
                        .consentAcceptedAt(LocalDateTime.now())
                        .build());

        List<Long> guarantorIds = r.getGuarantorIds() == null ? List.of() : r.getGuarantorIds();
        BigDecimal amountPerGuarantor = guarantorIds.isEmpty() ? BigDecimal.ZERO
                : amount.divide(BigDecimal.valueOf(guarantorIds.size()), 2, RoundingMode.HALF_UP);
        for (Long guarantorId : guarantorIds) {
            guarantors.save(LoanGuarantor.builder()
                    .loan(loan)
                    .groupMember(members.findById(guarantorId).orElseThrow())
                    .guaranteedAmount(amountPerGuarantor)
                    .build());
        }

        return response(loan);
    }

    private void validateGuarantors(Long groupId, GroupMember applicant, List<Long> requestedIds, Integer requiredCount) {
        int required = requiredCount == null ? 0 : requiredCount;
        List<Long> ids = requestedIds == null ? List.of() : requestedIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.size() != required) {
            throw new IllegalArgumentException("This group requires exactly " + required + " loan guarantor(s).");
        }
        for (Long id : ids) {
            GroupMember guarantor = members.findByIdForUpdate(id)
                    .filter(candidate -> candidate.getGroup().getId().equals(groupId)
                            && candidate.getStatus() == MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("Every guarantor must belong to this group."));
            if (guarantor.getId().equals(applicant.getId())) {
                throw new IllegalArgumentException("An applicant cannot guarantee their own loan.");
            }
            if (!loans.findOpenByGroupMemberId(id).isEmpty())
                throw new IllegalArgumentException("A selected guarantor has an open loan.");
            if (guarantorCommitted(id)) {
                throw new IllegalArgumentException("A selected guarantor is already committed to another open loan.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<LoanGuaranteeRequest> guaranteeInbox(Long groupId) {
        GroupMember self = authorizationService.requireCurrentMembership(groupId);
        return guarantors.findByGroupMemberIdAndStatus(self.getId(), GuarantorStatus.PENDING).stream()
                .filter(item -> item.getLoan().getGroupMember().getGroup().getId().equals(groupId)
                        && item.getLoan().getStatus() == LoanStatus.PENDING)
                .map(item -> new LoanGuaranteeRequest(item.getId(), item.getLoan().getId(),
                        item.getLoan().getLoanNumber(), item.getLoan().getGroupMember().getMember().getFirstName() + " "
                                + item.getLoan().getGroupMember().getMember().getLastName(),
                        item.getGuaranteedAmount(), item.getLoan().getPurpose(), item.getStatus().name()))
                .toList();
    }

    @Transactional
    public LoanResponse decideGuarantee(Long groupId, Long guaranteeId, boolean accept) {
        GroupMember self = authorizationService.requireCurrentMembership(groupId);
        LoanGuarantor guarantee = guarantors.findById(guaranteeId)
                .orElseThrow(() -> new IllegalArgumentException("Guarantee request not found."));
        Loan loan = guarantee.getLoan();
        if (!guarantee.getGroupMember().getId().equals(self.getId())
                || !loan.getGroupMember().getGroup().getId().equals(groupId))
            throw new AccessDeniedException("This guarantee request is not assigned to you.");
        if (loan.getStatus() != LoanStatus.PENDING || guarantee.getStatus() != GuarantorStatus.PENDING)
            throw new IllegalArgumentException("This guarantee request is no longer pending.");
        guarantee.setStatus(accept ? GuarantorStatus.ACCEPTED : GuarantorStatus.REJECTED);
        if (accept) guarantee.setApprovedAt(LocalDateTime.now());
        guarantors.saveAndFlush(guarantee);
        if (accept && guarantors.findByLoanId(loan.getId()).stream()
                .allMatch(item -> item.getStatus() == GuarantorStatus.ACCEPTED))
            loan.setStatus(LoanStatus.UNDER_REVIEW);
        return response(loan);
    }

    @Transactional
    public LoanResponse replaceGuarantor(Long groupId, Long loanId, Long rejectedGuarantorId, Long replacementId) {
        GroupMember self = authorizationService.requireCurrentMembership(groupId);
        Loan loan = require(loanId, groupId);
        if (!loan.getGroupMember().getId().equals(self.getId()))
            throw new AccessDeniedException("Only the applicant can replace a guarantor.");
        if (loan.getStatus() != LoanStatus.PENDING)
            throw new IllegalArgumentException("This loan is no longer waiting for guarantors.");
        LoanGuarantor rejected = guarantors.findById(rejectedGuarantorId)
                .filter(item -> item.getLoan().getId().equals(loanId) && item.getStatus() == GuarantorStatus.REJECTED)
                .orElseThrow(() -> new IllegalArgumentException("Select a rejected guarantor to replace."));
        List<Long> otherIds = guarantors.findByLoanId(loanId).stream()
                .filter(item -> !item.getId().equals(rejected.getId())).map(item -> item.getGroupMember().getId()).toList();
        if (otherIds.contains(replacementId)) throw new IllegalArgumentException("This guarantor is already selected.");
        validateGuarantors(groupId, self, List.of(replacementId), 1);
        guarantors.delete(rejected);
        guarantors.flush();
        guarantors.save(LoanGuarantor.builder().loan(loan)
                .groupMember(members.findById(replacementId).orElseThrow())
                .guaranteedAmount(rejected.getGuaranteedAmount()).build());
        return response(loan);
    }

    @Transactional(readOnly = true)
    public List<ShareApprovalStepConfig> approvalConfig(Long groupId) {
        authorizationService.requireMembership(groupId);
        return loanApprovalWorkflow.get(groupId);
    }

    @Transactional
    public List<ShareApprovalStepConfig> configureApproval(Long groupId, List<ShareApprovalStepConfig> steps) {
        authorizationService.requirePermission(groupId, "WORKFLOW_MANAGE");
        loanApprovalWorkflow.configure(authorizationService.requireCurrentMembership(groupId).getGroup(), steps);
        return loanApprovalWorkflow.get(groupId);
    }

    private List<LoanApprovalStep> steps(Loan loan) {
        var saved = approvalSteps.findByLoanIdOrderByStepOrderAsc(loan.getId());
        if (!saved.isEmpty() || loan.getStatus() != LoanStatus.UNDER_REVIEW) return saved;
        var config = loanApprovalWorkflow.get(loan.getGroupMember().getGroup().getId());
        for (int i = 0; i < config.size(); i++) {
            var item = config.get(i);
            approvalSteps.save(LoanApprovalStep.builder().loan(loan).stepOrder(i + 1)
                    .requiredRole(item.role()).label(item.label()).build());
        }
        return approvalSteps.findByLoanIdOrderByStepOrderAsc(loan.getId());
    }

    private LoanApprovalStep currentStep(Loan loan) {
        return steps(loan).stream().filter(step -> step.getApprovedAt() == null).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("All review steps are already complete."));
    }

    private void requireReviewer(Long groupId, LoanApprovalStep step, Loan loan) {
        var actor = authorizationService.requireCurrentMembership(groupId);
        if (actor.getId().equals(loan.getGroupMember().getId()))
            throw new AccessDeniedException("You cannot approve your own loan.");
        var role = step.getRequiredRole();
        boolean matches = authorizationService.hasRole(groupId, role)
                || (role == GroupRole.GROUP_CHAIRMAN && authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON))
                || (role == GroupRole.CHAIRPERSON && authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN));
        if (!matches) throw new AccessDeniedException("This loan is waiting for the " + step.getLabel() + ".");
    }

    private void event(Loan loan, Integer stepOrder, String action, String reason, Long actorId) {
        approvalEvents.save(LoanApprovalEvent.builder().loan(loan).stepOrder(stepOrder).action(action)
                .reason(reason).actorMemberId(actorId).actedAt(LocalDateTime.now()).build());
    }

    @Transactional
    public LoanResponse approve(Long groupId, Long id) {
        Loan l = requireForUpdate(id, groupId);
        if (l.getStatus() != LoanStatus.UNDER_REVIEW)
            throw new IllegalArgumentException("Wait for all guarantors to accept before approving this loan.");
        int required = settings.findByGroupId(groupId).map(GroupSettings::getRequiredLoanGuarantors).orElse(0);
        List<LoanGuarantor> selected = guarantors.findByLoanId(id);
        if (selected.size() != required || selected.stream().anyMatch(item -> item.getStatus() != GuarantorStatus.ACCEPTED))
            throw new IllegalArgumentException("All required guarantors must accept before loan approval.");
        LoanApprovalStep step = currentStep(l);
        requireReviewer(groupId, step, l);
        Long actorId = authorizationService.requireCurrentMembership(groupId).getId();
        step.setApprovedAt(LocalDateTime.now());
        step.setApprovedByMemberId(actorId);
        approvalSteps.save(step);
        event(l, step.getStepOrder(), "APPROVED", null, actorId);
        if (approvalSteps.findByLoanIdOrderByStepOrderAsc(id).stream().allMatch(item -> item.getApprovedAt() != null)) {
            l.setApprovalDate(LocalDate.now());
            activateLoan(l);
        }
        return response(l);
    }

    @Transactional
    public LoanResponse returnForReview(Long groupId, Long id, LoanDecisionRequest request) {
        Loan l = requireForUpdate(id, groupId);
        if (l.getStatus() != LoanStatus.UNDER_REVIEW) throw new IllegalArgumentException("Loan is not under review.");
        var all = steps(l);
        var current = currentStep(l);
        requireReviewer(groupId, current, l);
        if (current.getStepOrder() == 1) throw new IllegalArgumentException("The first reviewer cannot return to an earlier step; reject or cancel this application instead.");
        var previous = all.get(current.getStepOrder() - 2);
        previous.setApprovedAt(null);
        previous.setApprovedByMemberId(null);
        approvalSteps.save(previous);
        event(l, current.getStepOrder(), "RETURNED", required(request.getRejectionReason(), "return reason"),
                authorizationService.requireCurrentMembership(groupId).getId());
        return response(l);
    }

    @Transactional
    public LoanResponse reject(Long groupId, Long id, LoanDecisionRequest request) {
        Loan l = requireForUpdate(id, groupId);
        if (l.getStatus() != LoanStatus.UNDER_REVIEW) throw new IllegalArgumentException("Only applications under review can be rejected.");
        var step = currentStep(l);
        requireReviewer(groupId, step, l);
        String reason = required(request.getRejectionReason(), "rejection reason");
        l.setStatus(LoanStatus.REJECTED);
        l.setRejectionReason(reason);
        event(l, step.getStepOrder(), "REJECTED", reason, authorizationService.requireCurrentMembership(groupId).getId());
        return response(l);
    }

    @Transactional
    public LoanResponse cancel(Long groupId, Long id, LoanDecisionRequest request) {
        Loan l = requireForUpdate(id, groupId);
        if (l.getStatus() != LoanStatus.PENDING && l.getStatus() != LoanStatus.UNDER_REVIEW)
            throw new IllegalArgumentException("Only an undistributed application can be cancelled.");
        var actor = authorizationService.requireCurrentMembership(groupId);
        if (!actor.getId().equals(l.getGroupMember().getId())) requireReviewer(groupId, currentStep(l), l);
        l.setStatus(LoanStatus.CANCELLED);
        String reason = request == null ? null : request.getRejectionReason();
        l.setRejectionReason(reason);
        event(l, null, "CANCELLED", reason, actor.getId());
        return response(l);
    }

    @Transactional
    public LoanResponse disburse(Long groupId, Long id) {
        throw new IllegalArgumentException("Disbursement happens automatically after the final loan approval.");
    }

    private void activateLoan(Loan l) {
        LocalDate endDate = l.getGroupMember().getGroup().getEndDate();
        if (endDate == null || LocalDate.now().plusMonths(l.getDurationMonths()).isAfter(endDate))
            throw new IllegalArgumentException("The repayment schedule would extend beyond the Kikoba end date. Review this loan before disbursement.");
        l.setStatus(LoanStatus.ACTIVE);
        l.setDisbursementDate(LocalDate.now());
        l.setMaturityDate(LocalDate.now().plusMonths(l.getDurationMonths()));
        createSchedule(l);
        smsNotificationService.send(l.getGroupMember().getMember().getPhone(), "VIKOBA360: Hongera! Mkopo "
                + l.getLoanNumber() + " umetolewa kwa TZS " + l.getPrincipalAmount().toPlainString()
                + ". Angalia ratiba ya marejesho kwenye akaunti yako.");
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

    private Loan requireForUpdate(Long id, Long gid) {
        return loans.findLockedById(id).filter(l -> l.getGroupMember().getGroup().getId().equals(gid))
                .orElseThrow(() -> new IllegalArgumentException("Loan not found in this group."));
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
                .consentAcceptedAt(l.getConsentAcceptedAt())
                .approvalSteps(approvalSteps.findByLoanIdOrderByStepOrderAsc(l.getId()).stream().map(item ->
                        new LoanApprovalStepResponse(item.getStepOrder(), item.getRequiredRole().name(), item.getLabel(), item.getApprovedAt(), item.getApprovedByMemberId())).toList())
                .approvalEvents(approvalEvents.findByLoanIdOrderByActedAtAscIdAsc(l.getId()).stream().map(item ->
                        new LoanApprovalEventResponse(item.getStepOrder() == null ? 0 : item.getStepOrder(), item.getAction(), item.getActorMemberId(), item.getReason(), item.getActedAt())).toList())
                .guarantors(guarantors.findByLoanId(l.getId()).stream().map(item -> new LoanGuarantorOption(
                        item.getId(), item.getGroupMember().getMember().getFirstName() + " " + item.getGroupMember().getMember().getLastName(),
                        item.getGroupMember().getMember().getPhone(), item.getGroupMember().getMember().getAddress(),
                        item.getGroupMember().getMembershipNumber(), item.getStatus().name(), false, null)).toList())
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
