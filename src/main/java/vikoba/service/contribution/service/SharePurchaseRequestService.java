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
    private final ShareService shareService;

    @Transactional
    public SharePurchaseRequestResponse submit(Long groupId, Long groupMemberId, BigDecimal amount,
            Integer quantity, String paymentMethod, String paymentReference, String proofText,
            MultipartFile proofFile) {
        GroupMember member = groupMemberRepository.findById(groupMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Group member not found"));
        if (!member.getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Member does not belong to this group");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Enter a positive amount");

        BigDecimal unitPrice = groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group settings not found"))
                .getSharePrice();
        if (unitPrice == null || unitPrice.signum() <= 0)
            throw new IllegalArgumentException("Share price is not configured for this group");
        int resolvedQuantity = quantity != null && quantity > 0
                ? quantity
                : amount.divide(unitPrice, 0, RoundingMode.DOWN).intValue();
        if (resolvedQuantity <= 0)
            throw new IllegalArgumentException("The amount must purchase at least one share");

        ShareProduct product = shareProductRepository.findByGroupIdAndCode(groupId, "STANDARD")
                .orElseGet(() -> shareProductRepository.save(ShareProduct.builder()
                        .group(vikobaGroupRepository.findById(groupId)
                                .orElseThrow(() -> new IllegalArgumentException("Group not found")))
                        .code("STANDARD").name("Group Share").sharePrice(unitPrice).active(true).build()));
        SharePurchaseRequestEntity entity = SharePurchaseRequestEntity.builder()
                .groupMember(member).shareProduct(product).quantity(resolvedQuantity).amount(amount)
                .paymentMethod(paymentMethod == null || paymentMethod.isBlank() ? "CASH" : paymentMethod)
                .paymentReference(blankToNull(paymentReference)).proofText(blankToNull(proofText))
                .submittedAt(LocalDateTime.now()).build();
        if (proofFile != null && !proofFile.isEmpty()) {
            try {
                entity.setProofFile(proofFile.getBytes());
                entity.setProofFileName(proofFile.getOriginalFilename());
                entity.setProofContentType(proofFile.getContentType());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Unable to read the proof file");
            }
        }
        return map(requestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SharePurchaseRequestResponse> list(Long groupId, SharePurchaseRequestStatus status) {
        List<SharePurchaseRequestEntity> requests = status == null
                ? requestRepository.findByGroupMemberGroupIdOrderBySubmittedAtDesc(groupId)
                : requestRepository.findByGroupMemberGroupIdAndStatusOrderBySubmittedAtDesc(groupId, status);
        return requests.stream().map(this::map).toList();
    }

    @Transactional
    public SharePurchaseRequestResponse approve(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (request.getStatus() != SharePurchaseRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending requests can be approved");
        var purchase = new vikoba.service.contribution.dto.SharePurchaseRequest();
        purchase.setGroupMemberId(request.getGroupMember().getId());
        purchase.setQuantity(request.getQuantity());
        purchase.setAmount(request.getAmount());
        purchase.setPaymentMethod(request.getPaymentMethod());
        purchase.setReference(request.getPaymentReference());
        shareService.purchase(groupId, purchase);
        request.setStatus(SharePurchaseRequestStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        return map(requestRepository.save(request));
    }

    @Transactional
    public SharePurchaseRequestResponse reject(Long groupId, Long requestId, String reason) {
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (request.getStatus() != SharePurchaseRequestStatus.PENDING)
            throw new IllegalArgumentException("Only pending requests can be rejected");
        request.setStatus(SharePurchaseRequestStatus.REJECTED);
        request.setReviewReason(blankToNull(reason));
        request.setReviewedAt(LocalDateTime.now());
        return map(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public byte[] proof(Long groupId, Long requestId) {
        SharePurchaseRequestEntity request = findInGroup(groupId, requestId);
        if (request.getProofFile() == null)
            throw new IllegalArgumentException("Proof file not found");
        return request.getProofFile();
    }

    public String proofContentType(Long groupId, Long requestId) {
        return findInGroup(groupId, requestId).getProofContentType();
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
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod()).paymentReference(request.getPaymentReference())
                .proofText(request.getProofText()).proofFileName(request.getProofFileName())
                .proofContentType(request.getProofContentType()).hasProofFile(request.getProofFile() != null)
                .status(request.getStatus().name()).reviewReason(request.getReviewReason())
                .submittedAt(request.getSubmittedAt()).reviewedAt(request.getReviewedAt()).build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
