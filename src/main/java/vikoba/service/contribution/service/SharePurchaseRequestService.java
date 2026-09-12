package vikoba.service.contribution.service;

import lombok.RequiredArgsConstructor;
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
        BigDecimal minimum = settings.getMinimumSharePurchaseAmount();
        if (minimum != null && amount.compareTo(minimum) < 0)
            throw new IllegalArgumentException("The minimum share purchase amount is " + minimum.toPlainString());
        BigDecimal[] division = amount.divideAndRemainder(unitPrice);
        if (division[1].compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalArgumentException("Share amount must be an exact multiple of the configured share price");
        int resolvedQuantity = division[0].intValueExact();
        if (resolvedQuantity <= 0)
            throw new IllegalArgumentException("The amount must purchase at least one share");
        if (quantity != null && quantity > 0 && quantity != resolvedQuantity)
            throw new IllegalArgumentException("Share quantity must match the amount and configured share price");
        BigDecimal jamiiAmount = settings.getJamiiContributionPerSharePayment() == null
                ? BigDecimal.ZERO : settings.getJamiiContributionPerSharePayment();

        ShareProduct product = shareProductRepository.findByGroupIdAndCode(groupId, "STANDARD")
                .orElseGet(() -> shareProductRepository.save(ShareProduct.builder()
                        .group(vikobaGroupRepository.findById(groupId)
                                .orElseThrow(() -> new IllegalArgumentException("Group not found")))
                        .code("STANDARD").name("Group Share").sharePrice(unitPrice).active(true).build()));
        SharePurchaseRequestEntity entity = SharePurchaseRequestEntity.builder()
                .groupMember(member).shareProduct(product).quantity(resolvedQuantity).amount(amount).jamiiAmount(jamiiAmount)
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

    @Transactional
    public SharePurchaseRequestResponse approve(Long groupId, Long requestId) {
        assertReviewer(groupId);
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (request.getStatus() != SharePurchaseRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending requests can be approved");
        GroupMember reviewer = authorizationService.requireCurrentMembership(groupId);
        if (reviewer.getId().equals(request.getGroupMember().getId()))
            throw new IllegalArgumentException("You cannot approve your own share purchase request");

        boolean isAdmin = authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN);
        boolean isAccountant = authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT)
                || authorizationService.hasPermission(groupId, "SHARE_PURCHASE_APPROVE");
        boolean isChair = authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN)
                || authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON);
        LocalDateTime now = LocalDateTime.now();
        if (isAdmin) {
            request.setAccountantApprovedAt(now);
            request.setChairApprovedAt(now);
        } else {
            if (isAccountant && request.getAccountantApprovedAt() == null)
                request.setAccountantApprovedAt(now);
            if (isChair && request.getChairApprovedAt() == null)
                request.setChairApprovedAt(now);
        }
        if (request.getAccountantApprovedAt() == null || request.getChairApprovedAt() == null)
            return map(requestRepository.save(request));

        var purchase = new vikoba.service.contribution.dto.SharePurchaseRequest();
        purchase.setGroupMemberId(request.getGroupMember().getId());
        purchase.setQuantity(request.getQuantity());
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
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
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
        assertReviewer(groupId);
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (request.getProofFile() == null)
            throw new IllegalArgumentException("Proof file not found");
        return request.getProofFile();
    }

    public String proofContentType(Long groupId, Long requestId) {
        assertReviewer(groupId);
        return findInGroup(groupId, requestId).getProofContentType();
    }

    private void assertReviewer(Long groupId) {
        boolean allowed = authorizationService.hasRole(groupId, GroupRole.GROUP_ADMIN)
                || authorizationService.hasRole(groupId, GroupRole.ACCOUNTANT)
                || authorizationService.hasRole(groupId, GroupRole.GROUP_CHAIRMAN)
                || authorizationService.hasRole(groupId, GroupRole.CHAIRPERSON)
                || authorizationService.hasPermission(groupId, "SHARE_PURCHASE_APPROVE");
        if (!allowed)
            throw new AccessDeniedException("You do not have permission to review share purchase proofs");
    }

    private SharePurchaseRequestEntity findInGroup(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Share purchase request not found"));
        if (!request.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Request does not belong to this group");
        return request;
    }

    private SharePurchaseRequestResponse map(SharePurchaseRequestEntity request) {
        GroupMember member = request.getGroupMember();
        return SharePurchaseRequestResponse.builder().id(request.getId()).groupMemberId(member.getId())
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
