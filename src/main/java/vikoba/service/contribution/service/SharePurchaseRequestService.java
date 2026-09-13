package vikoba.service.contribution.service;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import vikoba.service.contribution.dto.ShareApprovalStep;
import vikoba.service.organization.service.ShareApprovalWorkflowService;
import vikoba.service.organization.repository.MemberRoleRepository;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.contribution.dto.SharePurchaseRequestResponse;
import vikoba.service.contribution.entity.ShareProduct;
import vikoba.service.contribution.entity.SharePurchaseRequestEntity;
import vikoba.service.contribution.entity.SharePurchaseRequestStatus;
import vikoba.service.contribution.repository.SharePurchaseRequestRepository;
import vikoba.service.contribution.repository.ShareProductRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.common.enums.GroupRole;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SharePurchaseRequestService {
    private final SharePurchaseRequestRepository requestRepository;
    private final ShareProductRepository shareProductRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final VikobaGroupRepository vikobaGroupRepository;
    private final GroupAuthorizationService authorizationService;
    private final ShareService shareService;
    private final ShareApprovalWorkflowService workflowService;
    private final MemberRoleRepository memberRoleRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SharePurchaseRequestResponse submit(Long groupId, BigDecimal amount,
            Integer quantity, String paymentMethod, String paymentReference, String proofText,
            MultipartFile proofFile) {
        GroupMember member = authorizationService.requireCurrentMembership(groupId);
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Enter a positive amount");
        requireProof(proofFile);

        var settings = groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group settings not found"))
                ;
        BigDecimal unitPrice = settings.getSharePrice();
        if (unitPrice == null || unitPrice.signum() <= 0)
            throw new IllegalArgumentException("Share price is not configured for this group");
        BigDecimal jamiiAmount = settings.getJamiiContributionPerSharePayment();
        if (jamiiAmount == null || jamiiAmount.signum() <= 0)
            throw new IllegalArgumentException("Jamii is not configured. Ask your group admin to configure the Jamii amount first.");
        BigDecimal shareAmount = amount.subtract(jamiiAmount);
        if (shareAmount.signum() <= 0)
            throw new IllegalArgumentException("The total payment must be greater than the Jamii amount of " + jamiiAmount.toPlainString());
        BigDecimal minimum = settings.getMinimumSharePurchaseAmount();
        if (minimum != null && shareAmount.compareTo(minimum) < 0)
            throw new IllegalArgumentException("The share amount after Jamii must be at least " + minimum.toPlainString());
        BigDecimal resolvedQuantity = shareAmount.divide(unitPrice, 8, RoundingMode.HALF_UP);
        if (resolvedQuantity.signum() <= 0)
            throw new IllegalArgumentException("The amount is too small to purchase shares");
        if (quantity != null && resolvedQuantity.compareTo(BigDecimal.valueOf(quantity)) != 0)
            throw new IllegalArgumentException("Share quantity must match the amount and configured share price");

        ShareProduct product = shareProductRepository.findByGroupIdAndCode(groupId, "STANDARD")
                .orElseGet(() -> shareProductRepository.save(ShareProduct.builder()
                        .group(vikobaGroupRepository.findById(groupId)
                                .orElseThrow(() -> new IllegalArgumentException("Group not found")))
                        .code("STANDARD").name("Group Share").sharePrice(unitPrice).active(true).build()));
        SharePurchaseRequestEntity entity = SharePurchaseRequestEntity.builder()
                .groupMember(member).shareProduct(product).quantity(resolvedQuantity).amount(shareAmount).jamiiAmount(jamiiAmount)
                .paymentMethod(paymentMethod == null || paymentMethod.isBlank() ? "CASH" : paymentMethod)
                .paymentReference(blankToNull(paymentReference)).proofText(blankToNull(proofText))
                .submittedAt(LocalDateTime.now()).build();
        try {
            entity.setProofFile(proofFile.getBytes());
            entity.setProofFileName(proofFile.getOriginalFilename());
            entity.setProofContentType(proofFile.getContentType());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read the proof file");
        }
        entity.setApprovalStepsJson(writeSteps(initialSteps(groupId, member)));
        return map(requestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SharePurchaseRequestResponse> list(Long groupId, SharePurchaseRequestStatus status) {
        assertReviewer(groupId);
        List<SharePurchaseRequestEntity> requests = status == null
                ? requestRepository.findByGroupMemberGroupIdOrderBySubmittedAtDesc(groupId)
                : requestRepository.findByGroupMemberGroupIdAndStatusOrderBySubmittedAtDesc(groupId, status);
        return requests.stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<SharePurchaseRequestResponse> listMine(Long groupId) {
        GroupMember member = authorizationService.requireCurrentMembership(groupId);
        return requestRepository.findByGroupMemberIdOrderBySubmittedAtDesc(member.getId())
                .stream().map(this::map).toList();
    }

    @Transactional
    public SharePurchaseRequestResponse approve(Long groupId, Long requestId) {
        assertReviewer(groupId);
        SharePurchaseRequestEntity request = findInGroupForUpdate(groupId, requestId);
        if (request.getStatus() != SharePurchaseRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending requests can be approved");
        GroupMember reviewer = authorizationService.requireCurrentMembership(groupId);
        if (reviewer.getId().equals(request.getGroupMember().getId()))
            throw new IllegalArgumentException("You cannot approve your own share purchase request");

        var steps = readSteps(request);
        if (steps.isEmpty()) steps = initialSteps(groupId, request.getGroupMember());
        int next = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).skipped() && steps.get(i).approvedAt() == null) { next = i; break; }
        }
        if (next < 0) throw new IllegalArgumentException("No independent approval step is available. Ask a group admin to configure another reviewer.");
        var current = steps.get(next);
        GroupRole requiredRole = GroupRole.valueOf(current.role());
        if (!canApproveCurrentStep(groupId, requiredRole))
            throw new AccessDeniedException("Waiting for " + current.label() + " (" + current.role() + ")");
        LocalDateTime now = LocalDateTime.now();
        steps.set(next, new ShareApprovalStep(current.role(), current.label(), now.toString(), reviewer.getId(), false));
        request.setApprovalStepsJson(writeSteps(steps));
        if (requiredRole == GroupRole.ACCOUNTANT) request.setAccountantApprovedAt(now);
        if (requiredRole == GroupRole.GROUP_CHAIRMAN || requiredRole == GroupRole.CHAIRPERSON) request.setChairApprovedAt(now);
        if (steps.stream().anyMatch(step -> !step.skipped() && step.approvedAt() == null))
            return map(requestRepository.save(request));

        var purchase = new vikoba.service.contribution.dto.SharePurchaseRequest();
        purchase.setGroupMemberId(request.getGroupMember().getId());
        purchase.setQuantity(null);
        purchase.setAmount(request.getAmount());
        purchase.setPaymentMethod(request.getPaymentMethod());
        purchase.setReference(request.getPaymentReference());
        purchase.setJamiiAmount(request.getJamiiAmount());
        shareService.purchase(groupId, purchase);
        request.setStatus(SharePurchaseRequestStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        return map(requestRepository.save(request));
    }

    @Transactional
    public SharePurchaseRequestResponse reject(Long groupId, Long requestId, String reason) {
        assertReviewer(groupId);
        SharePurchaseRequestEntity request = findInGroupForUpdate(groupId, requestId);
        if (request.getStatus() != SharePurchaseRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending requests can be rejected");
        if (authorizationService.requireCurrentMembership(groupId).getId().equals(request.getGroupMember().getId()))
            throw new IllegalArgumentException("You cannot reject your own share purchase request");
        request.setStatus(SharePurchaseRequestStatus.REJECTED);
        request.setReviewReason(blankToNull(reason));
        request.setReviewedAt(LocalDateTime.now());
        return map(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public byte[] proof(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = requireProofViewer(groupId, requestId);
        if (request.getProofFile() == null)
            throw new IllegalArgumentException("Proof file not found");
        return request.getProofFile();
    }

    public String proofContentType(Long groupId, Long requestId) {
        return requireProofViewer(groupId, requestId).getProofContentType();
    }

    private SharePurchaseRequestEntity requireProofViewer(Long groupId, Long requestId) {
        GroupMember current = authorizationService.requireCurrentMembership(groupId);
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (current.getId().equals(request.getGroupMember().getId())) return request;
        assertReviewer(groupId);
        return request;
    }

    private void assertReviewer(Long groupId) {
        boolean allowed = authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)
                || authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT)
                || authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN)
                || authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON)
                || authorizationService.hasPermission(groupId, "SHARE_PURCHASE_APPROVE");
        if (!allowed) allowed = workflowService.get(groupId).stream()
                .anyMatch(step -> authorizationService.hasRole(groupId, step.role()));
        if (!allowed)
            throw new AccessDeniedException("You do not have permission to review share purchase proofs");
    }

    private SharePurchaseRequestEntity findInGroupForUpdate(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = requestRepository.findWithLockById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Share purchase request not found"));
        if (!request.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Request does not belong to this group");
        return request;
    }

    private SharePurchaseRequestEntity findInGroup(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Share purchase request not found"));
        if (!request.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Request does not belong to this group");
        return request;
    }

    private boolean canApproveCurrentStep(Long groupId, GroupRole requiredRole) {
        // A group admin has an explicit override for the current step, not for the
        // entire workflow. Other reviewers must hold its configured role.
        return (authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)
                && authorizationService.hasPermission(groupId, "SHARE_PURCHASE_APPROVE"))
                || authorizationService.hasRole(groupId, requiredRole)
                || (requiredRole == GroupRole.GROUP_CHAIRMAN && authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON))
                || (requiredRole == GroupRole.CHAIRPERSON && authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN));
    }

    private SharePurchaseRequestResponse map(SharePurchaseRequestEntity request) {
        GroupMember member = request.getGroupMember();
        var steps = readSteps(request);
        var next = steps.stream().filter(step -> !step.skipped() && step.approvedAt() == null).findFirst().orElse(null);
        Long groupId = member.getGroup().getId();
        boolean independentReviewer = !authorizationService.requireCurrentMembership(groupId).getId().equals(member.getId());
        boolean pending = request.getStatus() == SharePurchaseRequestStatus.PENDING;
        boolean canApprove = pending && independentReviewer && next != null
                && canApproveCurrentStep(groupId, GroupRole.valueOf(next.role()));
        boolean canReject = pending && independentReviewer && (canApprove || authorizationService.hasPermission(groupId, "SHARE_PURCHASE_APPROVE"));
        return SharePurchaseRequestResponse.builder().id(request.getId())
                .canApprove(canApprove).canReject(canReject)
                .approvalSteps(steps).currentStepRole(next == null ? null : next.role())
                .currentStepLabel(next == null ? null : next.label()).groupMemberId(member.getId())
                .memberName(member.getMember().getFirstName() + " " + member.getMember().getLastName())
                .membershipNumber(member.getMembershipNumber()).quantity(request.getQuantity())
                .amount(request.getAmount()).jamiiAmount(request.getJamiiAmount())
                .paymentMethod(request.getPaymentMethod()).paymentReference(request.getPaymentReference())
                .proofText(request.getProofText()).proofFileName(request.getProofFileName())
                .proofContentType(request.getProofContentType()).hasProofFile(request.getProofFile() != null)
                .status(request.getStatus().name()).reviewReason(request.getReviewReason())
                .submittedAt(request.getSubmittedAt()).reviewedAt(request.getReviewedAt())
                .accountantApprovedAt(request.getAccountantApprovedAt()).chairApprovedAt(request.getChairApprovedAt()).build();
    }

    private List<ShareApprovalStep> initialSteps(Long groupId, GroupMember buyer) {
        var buyerRoles = memberRoleRepository.findByGroupMemberIdAndActiveTrue(buyer.getId()).stream()
                .map(role -> role.getRole()).toList();
        var configured = workflowService.get(groupId);
        var steps = new ArrayList<ShareApprovalStep>();
        for (var config : configured) {
            boolean skip = buyerRoles.contains(config.role())
                    || (config.role() == GroupRole.GROUP_CHAIRMAN && buyerRoles.contains(GroupRole.CHAIRPERSON))
                    || (config.role() == GroupRole.CHAIRPERSON && buyerRoles.contains(GroupRole.GROUP_CHAIRMAN));
            steps.add(new ShareApprovalStep(config.role().name(), config.label(), null, null, skip));
        }
        // A buyer holding every configured role still needs an independent reviewer.
        if (steps.stream().allMatch(ShareApprovalStep::skipped))
            steps.add(new ShareApprovalStep(GroupRole.GROUP_ADMIN.name(), "Independent admin review", null, null, false));
        return steps;
    }

    private List<ShareApprovalStep> readSteps(SharePurchaseRequestEntity request) {
        if (request.getApprovalStepsJson() == null || request.getApprovalStepsJson().isBlank()) {
            var steps = initialSteps(request.getGroupMember().getGroup().getId(), request.getGroupMember());
            // Preserve approvals from requests created before this workflow was introduced.
            var result = new ArrayList<ShareApprovalStep>();
            for (var step : steps) {
                LocalDateTime approved = step.role().equals("ACCOUNTANT") ? request.getAccountantApprovedAt()
                        : step.role().equals("GROUP_CHAIRMAN") ? request.getChairApprovedAt() : null;
                result.add(new ShareApprovalStep(step.role(), step.label(), approved == null ? null : approved.toString(), null, step.skipped()));
            }
            return result;
        }
        try { return objectMapper.readValue(request.getApprovalStepsJson(), new TypeReference<List<ShareApprovalStep>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Invalid saved share approval workflow", ex); }
    }

    private String writeSteps(List<ShareApprovalStep> steps) {
        try { return objectMapper.writeValueAsString(steps); }
        catch (Exception ex) { throw new IllegalStateException("Unable to save share approval workflow", ex); }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireProof(MultipartFile proofFile) {
        if (proofFile == null || proofFile.isEmpty())
            throw new IllegalArgumentException("Attach a payment receipt or message screenshot as proof");
        if (proofFile.getSize() > 5 * 1024 * 1024)
            throw new IllegalArgumentException("Payment proof must be 5 MB or smaller");
        String contentType = proofFile.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || "application/pdf".equals(contentType)))
            throw new IllegalArgumentException("Payment proof must be an image or PDF receipt");
    }
}
