package vikoba.service.loan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.*;
import vikoba.service.accounting.service.AccountingService;
import vikoba.service.accounting.dto.*;
import vikoba.service.contribution.entity.Payment;
import vikoba.service.contribution.entity.PaymentAllocation;
import vikoba.service.contribution.repository.PaymentAllocationRepository;
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
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.LoanApprovalWorkflowService;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import vikoba.service.notification.SmsNotificationRequestedEvent;

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
    private final PaymentAllocationRepository paymentAllocations;
    private final LoanPaymentRepository loanPayments;
    private final FineRepository fines;
    private final FineTypeRepository fineTypes;
    private final LoanGuarantorRepository guarantors;
    private final GroupSettingsRepository groupSettingsRepository;
    private final GroupAuthorizationService authorizationService;
    private final LoanApprovalWorkflowService loanApprovalWorkflow;
    private final LoanApprovalStepRepository approvalSteps;
    private final LoanApprovalEventRepository approvalEvents;
    private final MemberRoleRepository memberRoles;
    private final AccountingService accountingService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public List<LoanResponse> list(Long groupId) {
        authorizationService.requireMembership(groupId);
        return loans.findByGroupId(groupId).stream().peek(loan -> {
            if (loan.getStatus() == LoanStatus.UNDER_REVIEW) steps(loan);
        }).map(this::response).toList();
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
                        .requiredGuarantorsAtApplication(settings.getRequiredLoanGuarantors())
                        .lateFineAtApplication(orZero(settings.getLatePaymentFine()))
                        .build());

        List<Long> guarantorIds = r.getGuarantorIds() == null ? List.of() : r.getGuarantorIds();
        BigDecimal amountPerGuarantor = guarantorIds.isEmpty() ? BigDecimal.ZERO
                : amount.divide(BigDecimal.valueOf(guarantorIds.size()), 2, RoundingMode.HALF_UP);
        for (Long guarantorId : guarantorIds) {
            GroupMember guarantor = members.findById(guarantorId).orElseThrow();
            guarantors.save(LoanGuarantor.builder()
                    .loan(loan)
                    .groupMember(guarantor)
                    .guaranteedAmount(amountPerGuarantor)
                    .build());
            notifyMember(guarantor, "VIKOBA360: Umeombwa kuwa mdhamini wa mkopo " + loan.getLoanNumber()
                    + " wa " + memberName(member) + ", kiasi TZS " + amount.toPlainString()
                    + ". Ingia kwenye mfumo ukubali au ukatae ombi hili.");
        }

        notifyMember(member, "VIKOBA360: Ombi lako la mkopo " + loan.getLoanNumber()
                + " la TZS " + amount.toPlainString() + " limewasilishwa. Tutakujulisha kila hatua.");

        if (loan.getStatus() == LoanStatus.UNDER_REVIEW) steps(loan);
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
        {
            loan.setStatus(LoanStatus.UNDER_REVIEW);
            steps(loan);
        }
        notifyMember(loan.getGroupMember(), "VIKOBA360: Mdhamini " + memberName(self) + " "
                + (accept ? "amekubali" : "amekataa") + " kudhamini mkopo wako " + loan.getLoanNumber()
                + (accept && loan.getStatus() == LoanStatus.UNDER_REVIEW
                        ? ". Wadhamini wote wamekubali; ombi limeingia kwenye hatua za uidhinishaji."
                        : ". Ingia kwenye mfumo kuona maelezo."));
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
        GroupMember replacement = members.findById(replacementId).orElseThrow();
        guarantors.save(LoanGuarantor.builder().loan(loan)
                .groupMember(replacement)
                .guaranteedAmount(rejected.getGuaranteedAmount()).build());
        notifyMember(replacement, "VIKOBA360: Umeombwa kuwa mdhamini mbadala wa mkopo "
                + loan.getLoanNumber() + " wa " + memberName(self)
                + ". Ingia kwenye mfumo ukubali au ukatae ombi hili.");
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
        var borrowerRoles = memberRoles.findByGroupMemberIdAndActiveTrue(loan.getGroupMember().getId()).stream()
                .map(MemberRole::getRole).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < config.size(); i++) {
            var item = config.get(i);
            boolean borrowerIsReviewer = borrowerRoles.contains(item.role())
                    || (item.role() == GroupRole.GROUP_CHAIRMAN && borrowerRoles.contains(GroupRole.CHAIRPERSON))
                    || (item.role() == GroupRole.CHAIRPERSON && borrowerRoles.contains(GroupRole.GROUP_CHAIRMAN));
            GroupRole role = item.role();
            String label = item.label();
            boolean skip = borrowerIsReviewer && i < config.size() - 1;
            if (borrowerIsReviewer && !skip) {
                boolean independentReviewerExists = members
                        .findByGroupIdAndStatus(loan.getGroupMember().getGroup().getId(), MembershipStatus.ACTIVE)
                        .stream()
                        .filter(candidate -> !candidate.getId().equals(loan.getGroupMember().getId()))
                        .flatMap(candidate -> memberRoles.findByGroupMemberIdAndActiveTrue(candidate.getId()).stream())
                        .map(MemberRole::getRole)
                        .anyMatch(candidateRole -> sameWorkflowRole(candidateRole, role));
                if (!independentReviewerExists)
                    throw new IllegalArgumentException("The applicant also holds the final "
                            + role.name().replace('_', ' ')
                            + " role. Assign that role to another active member so the loan can be independently approved and disbursed.");
                label = label + " (independent reviewer)";
            }
            approvalSteps.save(LoanApprovalStep.builder().loan(loan).stepOrder(i + 1)
                    .requiredRole(role).label(label).approvedAt(skip ? LocalDateTime.now() : null).build());
            if (skip) event(loan, i + 1, "SKIPPED_SELF", "Applicant cannot review their own loan", loan.getGroupMember().getId());
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

    private boolean sameWorkflowRole(GroupRole first, GroupRole second) {
        if (first == second) return true;
        return (first == GroupRole.GROUP_CHAIRMAN && second == GroupRole.CHAIRPERSON)
                || (first == GroupRole.CHAIRPERSON && second == GroupRole.GROUP_CHAIRMAN);
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
        List<LoanGuarantor> selected = guarantors.findByLoanId(id);
        int required = l.getRequiredGuarantorsAtApplication() == null ? selected.size()
                : l.getRequiredGuarantorsAtApplication();
        if (selected.size() != required || selected.stream().anyMatch(item -> item.getStatus() != GuarantorStatus.ACCEPTED))
            throw new IllegalArgumentException("All required guarantors must accept before loan approval.");
        LoanApprovalStep step = currentStep(l);
        requireReviewer(groupId, step, l);
        Long actorId = authorizationService.requireCurrentMembership(groupId).getId();
        step.setApprovedAt(LocalDateTime.now());
        step.setApprovedByMemberId(actorId);
        approvalSteps.save(step);
        event(l, step.getStepOrder(), "APPROVED", null, actorId);
        boolean finalApproval = approvalSteps.findByLoanIdOrderByStepOrderAsc(id).stream()
                .allMatch(item -> item.getApprovedAt() != null);
        if (finalApproval) {
            l.setApprovalDate(LocalDate.now());
            activateLoan(l);
        } else {
            notifyMember(l.getGroupMember(), "VIKOBA360: Ombi lako la mkopo " + l.getLoanNumber()
                    + " limeidhinishwa katika hatua ya " + step.getLabel()
                    + ". Linaendelea kwenye hatua inayofuata.");
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
        var previous = all.stream().filter(item -> item.getStepOrder() < current.getStepOrder() && item.getApprovedByMemberId() != null)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("There is no earlier independent reviewer; reject or cancel this application instead."));
        previous.setApprovedAt(null);
        previous.setApprovedByMemberId(null);
        approvalSteps.save(previous);
        event(l, current.getStepOrder(), "RETURNED", required(request.getRejectionReason(), "return reason"),
                authorizationService.requireCurrentMembership(groupId).getId());
        notifyMember(l.getGroupMember(), "VIKOBA360: Ombi lako la mkopo " + l.getLoanNumber()
                + " limerudishwa kwa mapitio. Sababu: " + request.getRejectionReason().trim()
                + ". Ingia kwenye mfumo kuona hatua inayofuata.");
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
        notifyMember(l.getGroupMember(), "VIKOBA360: Ombi lako la mkopo " + l.getLoanNumber()
                + " limekataliwa. Sababu: " + reason + ".");
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
        notifyMember(l.getGroupMember(), "VIKOBA360: Ombi la mkopo " + l.getLoanNumber()
                + " limefutwa" + (reason == null || reason.isBlank() ? "." : ". Sababu: " + reason + "."));
        return response(l);
    }

    @Transactional
    public LoanResponse disburse(Long groupId, Long id) {
        Loan loan = requireForUpdate(id, groupId);
        if (loan.getStatus() != LoanStatus.APPROVED || !approvalSteps.findByLoanIdOrderByStepOrderAsc(id).isEmpty())
            throw new IllegalArgumentException("New loans disburse automatically after the final approval.");
        var actor = authorizationService.requireCurrentMembership(groupId);
        if (actor.getId().equals(loan.getGroupMember().getId()) || !authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT))
            throw new AccessDeniedException("Only an independent accountant may disburse this previously approved loan.");
        activateLoan(loan);
        event(loan, null, "DISBURSED_LEGACY", null, actor.getId());
        return response(loan);
    }

    private void activateLoan(Loan l) {
        LocalDate endDate = l.getGroupMember().getGroup().getEndDate();
        if (endDate == null || LocalDate.now().plusMonths(l.getDurationMonths()).isAfter(endDate))
            throw new IllegalArgumentException("The repayment schedule would extend beyond the Kikoba end date. Review this loan before disbursement.");
        l.setStatus(LoanStatus.ACTIVE);
        l.setDisbursementDate(LocalDate.now());
        l.setMaturityDate(LocalDate.now().plusMonths(l.getDurationMonths()));
        createSchedule(l);
        postDisbursement(l);
        notifyMember(l.getGroupMember(), "VIKOBA360: Hongera! Mkopo "
                + l.getLoanNumber() + " umetolewa kwa TZS " + l.getPrincipalAmount().toPlainString()
                + ". Angalia ratiba ya marejesho kwenye akaunti yako.");
    }

    private void postDisbursement(Loan loan) {
        Long groupId = loan.getGroupMember().getGroup().getId();
        var accounts = accountingService.ensureDefaultAccountsForGroup(groupId);
        Long receivableId = accounts.stream().filter(account -> "1100".equals(account.getCode()))
                .findFirst().orElseThrow().getId();
        Long cashId = accounts.stream().filter(account -> "1000".equals(account.getCode()))
                .findFirst().orElseThrow().getId();
        var debit = new JournalLineRequest();
        debit.setAccountId(receivableId);
        debit.setDebit(loan.getPrincipalAmount());
        debit.setDescription("Member " + loan.getGroupMember().getMembershipNumber() + " loan receivable");
        var credit = new JournalLineRequest();
        credit.setAccountId(cashId);
        credit.setCredit(loan.getPrincipalAmount());
        credit.setDescription("Loan disbursement to " + loan.getGroupMember().getMembershipNumber());
        var entry = new JournalEntryRequest();
        entry.setReference(loan.getLoanNumber());
        entry.setDescription("Loan disbursement " + loan.getLoanNumber());
        entry.setLines(List.of(debit, credit));
        accountingService.post(groupId, entry);
    }

    @Transactional
    public LoanRepaymentResponse repay(Long groupId, Long id, LoanRepaymentRequest r) {
        Loan l = requireForUpdate(id, groupId);
        if (l.getStatus() != LoanStatus.ACTIVE && l.getStatus() != LoanStatus.DEFAULTED)
            throw new IllegalArgumentException("This loan is not open for repayment.");
        GroupMember actor = authorizationService.requireCurrentMembership(groupId);
        if (!actor.getId().equals(l.getGroupMember().getId()))
            throw new AccessDeniedException("Only the borrower can submit this loan repayment.");
        BigDecimal amount = positive(r.getAmount(), "repayment amount");
        BigDecimal outstanding = schedule(l).stream().map(LoanInstallmentResponse::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = payments.findLoanRepaymentsByGroupId(groupId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .filter(payment -> paymentAllocations.findByPaymentId(payment.getId()).stream()
                        .anyMatch(allocation -> Objects.equals(allocation.getReferenceId(), id)))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(outstanding.subtract(pending)) > 0)
            throw new IllegalArgumentException("Repayment exceeds the outstanding loan balance.");
        Payment payment = payments.save(Payment.builder().group(l.getGroupMember().getGroup())
                .groupMember(l.getGroupMember()).reference("LRP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .externalReference(blank(r.getExternalReference())).amount(amount)
                .paymentMethod(parsePaymentMethod(r.getPaymentMethod())).status(PaymentStatus.PENDING)
                .paymentDate(LocalDateTime.now()).description("Loan repayment awaiting accountant approval for " + l.getLoanNumber()).build());
        paymentAllocations.save(PaymentAllocation.builder().payment(payment).type(PaymentAllocationType.LOAN_REPAYMENT)
                .amount(amount).referenceId(l.getId()).description("Loan repayment " + l.getLoanNumber()).build());
        notifyMember(l.getGroupMember(), "VIKOBA360: Marejesho ya mkopo " + l.getLoanNumber()
                + " ya TZS " + amount.toPlainString() + " yamewasilishwa na yanasubiri uthibitisho wa mhasibu.");
        return repaymentResponse(payment, l);
    }

    @Transactional(readOnly = true)
    public List<LoanRepaymentResponse> repayments(Long groupId) {
        GroupMember actor = authorizationService.requireCurrentMembership(groupId);
        boolean accountant = authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT);
        return payments.findLoanRepaymentsByGroupId(groupId).stream()
                .filter(payment -> accountant || payment.getGroupMember().getId().equals(actor.getId()))
                .map(payment -> {
            Long loanId = paymentAllocations.findByPaymentId(payment.getId()).stream()
                    .filter(allocation -> allocation.getType() == PaymentAllocationType.LOAN_REPAYMENT)
                    .map(PaymentAllocation::getReferenceId).findFirst().orElse(null);
            return repaymentResponse(payment, require(loanId, groupId));
                }).toList();
    }

    @Transactional
    public LoanRepaymentResponse approveRepayment(Long groupId, Long paymentId) {
        Payment payment = requirePendingRepayment(groupId, paymentId);
        Loan loan = repaymentLoan(groupId, payment);
        GroupMember accountant = requireIndependentAccountant(groupId, loan);
        BigDecimal left = payment.getAmount();
        BigDecimal principalTotal = BigDecimal.ZERO, interestTotal = BigDecimal.ZERO, penaltyTotal = BigDecimal.ZERO;
        for (LoanInstallment installment : installments.findByLoanIdOrderByInstallmentNumberAsc(loan.getId())) {
            if (left.signum() <= 0) break;
            BigDecimal already = installment.getPaidAmount();
            BigDecimal penaltyPaid = already.min(installment.getPenaltyAmount());
            already = already.subtract(penaltyPaid);
            BigDecimal interestPaid = already.min(installment.getInterestAmount());
            already = already.subtract(interestPaid);
            BigDecimal principalPaid = already.min(installment.getPrincipalAmount());
            BigDecimal penalty = left.min(installment.getPenaltyAmount().subtract(penaltyPaid).max(BigDecimal.ZERO));
            left = left.subtract(penalty);
            BigDecimal interest = left.min(installment.getInterestAmount().subtract(interestPaid).max(BigDecimal.ZERO));
            left = left.subtract(interest);
            BigDecimal principal = left.min(installment.getPrincipalAmount().subtract(principalPaid).max(BigDecimal.ZERO));
            left = left.subtract(principal);
            BigDecimal applied = penalty.add(interest).add(principal);
            if (applied.signum() == 0) continue;
            installment.setPaidAmount(installment.getPaidAmount().add(applied));
            installment.setStatus(installment.getPaidAmount().compareTo(installment.getTotalAmount()) >= 0
                    ? InstallmentStatus.PAID : InstallmentStatus.PARTIAL);
            installments.save(installment);
            loanPayments.save(LoanPayment.builder().loan(loan).installment(installment).payment(payment)
                    .principalAmount(principal).interestAmount(interest).penaltyAmount(penalty).totalAmount(applied).build());
            principalTotal = principalTotal.add(principal);
            interestTotal = interestTotal.add(interest);
            penaltyTotal = penaltyTotal.add(penalty);
        }
        if (left.signum() > 0) throw new IllegalArgumentException("Repayment exceeds the current outstanding balance.");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedByMemberId(accountant.getId());
        payment.setDescription("Approved loan repayment for " + loan.getLoanNumber());
        postRepayment(groupId, payment, principalTotal, interestTotal.add(penaltyTotal));
        List<LoanInstallmentResponse> updatedSchedule = schedule(loan);
        if (updatedSchedule.stream().allMatch(item -> item.getBalance().signum() == 0)) {
            loan.setStatus(LoanStatus.COMPLETED);
        } else if (loan.getStatus() == LoanStatus.DEFAULTED && updatedSchedule.stream()
                .noneMatch(item -> item.getDueDate().isBefore(LocalDate.now()) && item.getBalance().signum() > 0)) {
            loan.setStatus(LoanStatus.ACTIVE);
        }
        notifyMember(loan.getGroupMember(), "VIKOBA360: Marejesho ya TZS " + payment.getAmount().toPlainString()
                + " kwa mkopo " + loan.getLoanNumber() + " yamethibitishwa"
                + (loan.getStatus() == LoanStatus.COMPLETED ? ". Mkopo umelipwa kikamilifu." : "."));
        return repaymentResponse(payment, loan);
    }

    @Transactional
    public LoanRepaymentResponse rejectRepayment(Long groupId, Long paymentId, LoanDecisionRequest request) {
        Payment payment = requirePendingRepayment(groupId, paymentId);
        Loan loan = repaymentLoan(groupId, payment);
        GroupMember accountant = requireIndependentAccountant(groupId, loan);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedByMemberId(accountant.getId());
        payment.setRejectionReason(required(request.getRejectionReason(), "rejection reason"));
        notifyMember(loan.getGroupMember(), "VIKOBA360: Marejesho ya TZS " + payment.getAmount().toPlainString()
                + " kwa mkopo " + loan.getLoanNumber() + " yamekataliwa. Sababu: "
                + payment.getRejectionReason() + ".");
        return repaymentResponse(payment, loan);
    }

    @Transactional
    public int assessOverdue(Long groupId) {
        GroupSettings s = settings.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Loan settings not found."));
        int count = 0;
        for (Loan item : loans.findByGroupId(groupId)) {
            Loan l = requireForUpdate(item.getId(), groupId);
            if (l.getStatus() != LoanStatus.ACTIVE && l.getStatus() != LoanStatus.DEFAULTED)
                continue;
            BigDecimal fine = l.getLateFineAtApplication() == null ? orZero(s.getLatePaymentFine())
                    : l.getLateFineAtApplication();
            boolean hasOverdueBalance = false;
            for (LoanInstallment i : installments.findByLoanIdOrderByInstallmentNumberAsc(l.getId())) {
                BigDecimal balance = i.getTotalAmount().subtract(i.getPaidAmount()).max(BigDecimal.ZERO);
                long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), i.getDueDate());
                if ((daysUntilDue == 3 || daysUntilDue == 0) && balance.signum() > 0) {
                    notifyMember(l.getGroupMember(), "VIKOBA360: Kumbusho, marejesho ya mkopo "
                            + l.getLoanNumber() + " ya TZS " + balance.toPlainString()
                            + (daysUntilDue == 0 ? " yanatakiwa kulipwa leo." : " yanatakiwa kulipwa baada ya siku 3.")
                            + " Ingia kwenye mfumo kuona ratiba yako.");
                }
                if (i.getDueDate().isBefore(LocalDate.now()) && i.getPaidAmount().compareTo(i.getTotalAmount()) < 0) {
                    hasOverdueBalance = true;
                    i.setStatus(InstallmentStatus.OVERDUE);
                    if (fine.signum() > 0 && i.getPenaltyAmount().signum() == 0) {
                        i.setPenaltyAmount(fine);
                        i.setTotalAmount(i.getTotalAmount().add(fine));
                        count++;
                        notifyMember(l.getGroupMember(), "VIKOBA360: Mkopo " + l.getLoanNumber()
                                + " umechelewa kulipwa. Umeongezewa faini ya TZS " + fine.toPlainString()
                                + " kwa awamu ya tarehe " + i.getDueDate() + ".");
                    }
                }
            }
            if (hasOverdueBalance) l.setStatus(LoanStatus.DEFAULTED);
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
        Long groupId = l.getGroupMember().getGroup().getId();
        var savedSteps = approvalSteps.findByLoanIdOrderByStepOrderAsc(l.getId());
        var waitingStep = savedSteps.stream().filter(item -> item.getApprovedAt() == null).findFirst().orElse(null);
        var caller = authorizationService.requireCurrentMembership(groupId);
        boolean self = caller.getId().equals(l.getGroupMember().getId());
        boolean reviewer = l.getStatus() == LoanStatus.UNDER_REVIEW && !self && waitingStep != null &&
                (authorizationService.hasRole(groupId, waitingStep.getRequiredRole())
                        || (waitingStep.getRequiredRole() == GroupRole.GROUP_CHAIRMAN && authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON))
                        || (waitingStep.getRequiredRole() == GroupRole.CHAIRPERSON && authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN)));
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
                .latePaymentFine(l.getLateFineAtApplication() == null
                        ? settings.findByGroupId(l.getGroupMember().getGroup().getId())
                                .map(GroupSettings::getLatePaymentFine).orElse(BigDecimal.ZERO)
                        : l.getLateFineAtApplication())
                .consentAcceptedAt(l.getConsentAcceptedAt())
                .canApprove(reviewer).canDisburse(l.getStatus() == LoanStatus.APPROVED && !self && savedSteps.isEmpty() && authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT)).canCancel((l.getStatus() == LoanStatus.PENDING || l.getStatus() == LoanStatus.UNDER_REVIEW) && (self || reviewer))
                .approvalSteps(savedSteps.stream().map(item ->
                        new LoanApprovalStepResponse(item.getStepOrder(), item.getRequiredRole().name(), item.getLabel(), item.getApprovedAt(), item.getApprovedByMemberId())).toList())
                .approvalEvents(approvalEvents.findByLoanIdOrderByActedAtAscIdAsc(l.getId()).stream().map(item ->
                        new LoanApprovalEventResponse(item.getStepOrder() == null ? 0 : item.getStepOrder(), item.getAction(), item.getActorMemberId(), item.getReason(), item.getActedAt())).toList())
                .guarantors(guarantors.findByLoanId(l.getId()).stream().map(item -> new LoanGuarantorOption(
                        item.getId(), item.getGroupMember().getMember().getFirstName() + " " + item.getGroupMember().getMember().getLastName(),
                        item.getGroupMember().getMember().getPhone(), item.getGroupMember().getMember().getAddress(),
                        item.getGroupMember().getMembershipNumber(), item.getStatus().name(), false, null)).toList())
                .build();
    }

    private Payment requirePendingRepayment(Long groupId, Long paymentId) {
        Payment payment = payments.findLockedById(paymentId)
                .filter(item -> item.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException("Loan repayment request was not found in this group."));
        boolean loanRepayment = paymentAllocations.findByPaymentId(paymentId).stream()
                .anyMatch(item -> item.getType() == PaymentAllocationType.LOAN_REPAYMENT);
        if (!loanRepayment) throw new IllegalArgumentException("This payment is not a loan repayment.");
        if (payment.getStatus() != PaymentStatus.PENDING)
            throw new IllegalArgumentException("This repayment has already been reviewed.");
        return payment;
    }

    private Loan repaymentLoan(Long groupId, Payment payment) {
        Long loanId = paymentAllocations.findByPaymentId(payment.getId()).stream()
                .filter(item -> item.getType() == PaymentAllocationType.LOAN_REPAYMENT)
                .map(PaymentAllocation::getReferenceId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loan repayment allocation is missing."));
        return requireForUpdate(loanId, groupId);
    }

    private GroupMember requireIndependentAccountant(Long groupId, Loan loan) {
        GroupMember accountant = authorizationService.requireCurrentMembership(groupId);
        if (!authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT))
            throw new AccessDeniedException("Only the group accountant can review loan repayments.");
        if (accountant.getId().equals(loan.getGroupMember().getId()))
            throw new AccessDeniedException("You cannot approve your own loan repayment.");
        return accountant;
    }

    private LoanRepaymentResponse repaymentResponse(Payment payment, Loan loan) {
        GroupMember actor = authorizationService.requireCurrentMembership(loan.getGroupMember().getGroup().getId());
        boolean canApprove = payment.getStatus() == PaymentStatus.PENDING
                && !actor.getId().equals(loan.getGroupMember().getId())
                && authorizationService.hasRole(loan.getGroupMember().getGroup().getId(), GroupRole.ACCOUNTANT);
        return LoanRepaymentResponse.builder().id(payment.getId()).loanId(loan.getId())
                .loanNumber(loan.getLoanNumber()).groupMemberId(loan.getGroupMember().getId())
                .memberName(loan.getGroupMember().getMember().getFirstName() + " "
                        + loan.getGroupMember().getMember().getLastName())
                .amount(payment.getAmount()).paymentMethod(payment.getPaymentMethod().name())
                .externalReference(payment.getExternalReference()).status(payment.getStatus().name())
                .submittedAt(payment.getPaymentDate()).reviewedAt(payment.getReviewedAt())
                .rejectionReason(payment.getRejectionReason()).canApprove(canApprove).build();
    }

    private void postRepayment(Long groupId, Payment payment, BigDecimal principal, BigDecimal income) {
        var accounts = accountingService.ensureDefaultAccountsForGroup(groupId);
        Long cashId = accounts.stream().filter(account -> "1000".equals(account.getCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cash account is not configured.")).getId();
        Long receivableId = accounts.stream().filter(account -> "1100".equals(account.getCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loan receivable account is not configured.")).getId();
        Long incomeId = accounts.stream().filter(account -> "4000".equals(account.getCode())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Interest income account is not configured.")).getId();
        var cash = new JournalLineRequest();
        cash.setAccountId(cashId); cash.setDebit(payment.getAmount()); cash.setDescription("Loan repayment received");
        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(cash);
        if (principal.signum() > 0) {
            var receivable = new JournalLineRequest();
            receivable.setAccountId(receivableId); receivable.setCredit(principal); receivable.setDescription("Loan principal repaid");
            lines.add(receivable);
        }
        if (income.signum() > 0) {
            var interest = new JournalLineRequest();
            interest.setAccountId(incomeId); interest.setCredit(income); interest.setDescription("Loan interest and late fine received");
            lines.add(interest);
        }
        var entry = new JournalEntryRequest();
        entry.setReference(payment.getReference());
        entry.setDescription("Approved loan repayment " + payment.getReference());
        entry.setLines(lines);
        accountingService.post(groupId, entry);
    }

    private PaymentMethod parsePaymentMethod(String value) {
        if (value == null || value.isBlank()) return PaymentMethod.MOBILE_MONEY;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Choose a valid repayment method.");
        }
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal positive(BigDecimal n, String f) {
        if (n == null || n.signum() <= 0)
            throw new IllegalArgumentException(f + " must be greater than zero.");
        return n;
    }

    private BigDecimal orZero(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n;
    }

    private void notifyMember(GroupMember membership, String message) {
        if (membership == null || membership.getMember() == null) return;
        var person = membership.getMember();
        if (person.getPhone() == null || person.getPhone().isBlank()) return;
        eventPublisher.publishEvent(new SmsNotificationRequestedEvent(
                person.getPhone(), memberName(membership), message));
    }

    private String memberName(GroupMember membership) {
        if (membership == null || membership.getMember() == null) return "Member";
        String name = ((membership.getMember().getFirstName() == null ? "" : membership.getMember().getFirstName())
                + " " + (membership.getMember().getLastName() == null ? "" : membership.getMember().getLastName())).trim();
        return name.isBlank() ? "Member" : name;
    }

    private String required(String v, String f) {
        if (v == null || v.isBlank())
            throw new IllegalArgumentException(f + " is required.");
        return v.trim();
    }
}
