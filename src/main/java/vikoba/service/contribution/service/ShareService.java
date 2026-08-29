package vikoba.service.contribution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.ShareTransactionType;
import vikoba.service.contribution.dto.*;
import vikoba.service.contribution.entity.ShareProduct;
import vikoba.service.contribution.entity.ShareTransaction;
import vikoba.service.contribution.dto.RecordPaymentRequest;
import vikoba.service.common.enums.PaymentAllocationType;
import vikoba.service.contribution.repository.ShareProductRepository;
import vikoba.service.contribution.repository.ShareTransactionRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.GroupSettings;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.GroupSettingsRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import vikoba.service.notification.SmsNotificationService;

@Service
@RequiredArgsConstructor
public class ShareService {
    private static final String PRODUCT_CODE = "STANDARD";

    private final ShareProductRepository shareProductRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupSettingsRepository groupSettingsRepository;
    private final VikobaGroupRepository vikobaGroupRepository;
    private final PaymentService paymentService;
    private final SmsNotificationService smsNotificationService;

    @Transactional(readOnly = true)
    public ShareSummaryResponse getSummary(Long groupId) {
        ShareProduct product = getConfiguredProduct(groupId);
        List<ShareTransaction> ledger = shareTransactionRepository.findLedgerByGroupId(groupId);
        Map<Long, Integer> balances = calculateBalances(ledger);
        int totalShares = balances.values().stream().mapToInt(Integer::intValue).sum();
        return ShareSummaryResponse.builder()
                .unitPrice(product.getSharePrice())
                .totalShares(totalShares)
                .totalCapital(product.getSharePrice().multiply(BigDecimal.valueOf(totalShares)))
                .holdersCount((int) balances.values().stream().filter(value -> value > 0).count())
                .totalMembers(groupMemberRepository.countActiveMembersByGroupId(groupId).intValue())
                .maximumSharesPerMember(getSettings(groupId).getMaximumSharesPerMember())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShareOwnershipResponse> getOwnership(Long groupId) {
        ShareProduct product = getConfiguredProduct(groupId);
        List<ShareTransaction> ledger = shareTransactionRepository.findLedgerByGroupId(groupId);
        Map<Long, Integer> balances = calculateBalances(ledger);
        int totalShares = balances.values().stream().mapToInt(Integer::intValue).sum();
        Map<Long, ShareTransaction> members = ledger.stream()
                .collect(Collectors.toMap(st -> st.getGroupMember().getId(), Function.identity(),
                        (first, ignored) -> first));

        return balances.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> {
                    ShareTransaction transaction = members.get(entry.getKey());
                    int quantity = entry.getValue();
                    return ShareOwnershipResponse.builder()
                            .groupMemberId(entry.getKey())
                            .memberName(memberName(transaction.getGroupMember()))
                            .membershipNumber(transaction.getGroupMember().getMembershipNumber())
                            .sharesOwned(quantity)
                            .unitPrice(product.getSharePrice())
                            .equityValue(product.getSharePrice().multiply(BigDecimal.valueOf(quantity)))
                            .ownershipPercentage(totalShares == 0 ? 0 : quantity * 100d / totalShares)
                            .build();
                })
                .sorted(Comparator.comparing(ShareOwnershipResponse::getSharesOwned).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShareTransactionResponse> getLedger(Long groupId) {
        return shareTransactionRepository.findLedgerByGroupId(groupId).stream()
                .map(this::mapTransaction)
                .toList();
    }

    @Transactional
    public ShareTransactionResponse purchase(Long groupId, SharePurchaseRequest request) {
        ShareProduct product = getOrCreateProduct(groupId);
        GroupMember member = getMemberInGroup(request.getGroupMemberId(), groupId);
        int quantity = resolveQuantity(request.getQuantity(), request.getAmount(), product.getSharePrice());
        validateMaximum(member.getId(), groupId, quantity, product);
        BigDecimal amount = request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getAmount()
                : product.getSharePrice().multiply(BigDecimal.valueOf(quantity));
        ShareTransaction transaction = newTransaction(member, product, ShareTransactionType.PURCHASE,
                quantity, amount, request.getReference());
        ShareTransaction saved = shareTransactionRepository.save(transaction);
        RecordPaymentRequest payment = new RecordPaymentRequest();
        payment.setGroupMemberId(member.getId());
        payment.setAmount(amount);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setReference(request.getReference());
        payment.setAllocationType(PaymentAllocationType.SHARE_PURCHASE.name());
        payment.setAllocationReferenceId(saved.getId());
        payment.setDescription("Share purchase: " + quantity + " share(s)");
        paymentService.record(groupId, payment);
        smsNotificationService.send(member.getMember().getPhone(), "VIKOBA360: Hongera! Ununuzi wa hisa "
                + quantity + " umepokelewa kwa TZS " + amount.toPlainString() + ". Asante kwa kuweka akiba.");
        return mapTransaction(saved);
    }

    @Transactional
    public ShareTransactionResponse transfer(Long groupId, ShareTransferRequest request) {
        ShareProduct product = getOrCreateProduct(groupId);
        GroupMember from = getMemberInGroup(request.getFromGroupMemberId(), groupId);
        GroupMember to = getMemberInGroup(request.getToGroupMemberId(), groupId);
        if (Objects.equals(from.getId(), to.getId()))
            throw new IllegalArgumentException("Source and target members must differ");
        requirePositiveQuantity(request.getQuantity());
        int owned = calculateBalance(shareTransactionRepository.findLedgerByGroupId(groupId), from.getId());
        if (owned < request.getQuantity())
            throw new IllegalArgumentException("Member does not own enough shares");
        validateMaximum(to.getId(), groupId, request.getQuantity(), product);
        BigDecimal amount = product.getSharePrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        String reference = reference(request.getReference());
        shareTransactionRepository.save(newTransaction(from, product, ShareTransactionType.TRANSFER_OUT,
                request.getQuantity(), amount, reference + "-OUT"));
        return mapTransaction(
                shareTransactionRepository.save(newTransaction(to, product, ShareTransactionType.TRANSFER_IN,
                        request.getQuantity(), amount, reference + "-IN")));
    }

    @Transactional
    public ShareTransactionResponse redeem(Long groupId, ShareRedemptionRequest request) {
        ShareProduct product = getOrCreateProduct(groupId);
        GroupMember member = getMemberInGroup(request.getGroupMemberId(), groupId);
        requirePositiveQuantity(request.getQuantity());
        int owned = calculateBalance(shareTransactionRepository.findLedgerByGroupId(groupId), member.getId());
        if (owned < request.getQuantity())
            throw new IllegalArgumentException("Member does not own enough shares");
        BigDecimal amount = product.getSharePrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        ShareTransaction transaction = newTransaction(member, product, ShareTransactionType.REDEMPTION,
                request.getQuantity(), amount, request.getReference());
        return mapTransaction(shareTransactionRepository.save(transaction));
    }

    private ShareProduct getConfiguredProduct(Long groupId) {
        GroupSettings settings = getSettings(groupId);
        if (settings.getSharePrice() == null || settings.getSharePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Share price is not configured for this group");
        }
        return ShareProduct.builder()
                .sharePrice(settings.getSharePrice())
                .maximumShares(settings.getMaximumSharesPerMember())
                .code(PRODUCT_CODE)
                .name("Group Share")
                .active(true)
                .build();
    }

    private ShareProduct getOrCreateProduct(Long groupId) {
        GroupSettings settings = getSettings(groupId);
        if (settings.getSharePrice() == null || settings.getSharePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Share price is not configured for this group");
        }
        ShareProduct product = shareProductRepository.findByGroupIdAndCode(groupId, PRODUCT_CODE)
                .orElseGet(() -> ShareProduct.builder()
                        .group(vikobaGroupRepository.findById(groupId)
                                .orElseThrow(() -> new IllegalArgumentException("Group not found")))
                        .code(PRODUCT_CODE).name("Group Share").active(true).build());
        product.setSharePrice(settings.getSharePrice());
        product.setMaximumShares(settings.getMaximumSharesPerMember());
        return shareProductRepository.save(product);
    }

    private GroupSettings getSettings(Long groupId) {
        return groupSettingsRepository.findByGroupId(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group settings not found"));
    }

    private GroupMember getMemberInGroup(Long memberId, Long groupId) {
        if (memberId == null)
            throw new IllegalArgumentException("groupMemberId is required");
        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Group member not found"));
        if (!member.getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Member does not belong to this group");
        return member;
    }

    private int resolveQuantity(Integer quantity, BigDecimal amount, BigDecimal unitPrice) {
        if (quantity != null && quantity > 0) {
            return quantity;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Enter a positive share quantity or amount");
        int calculatedQuantity = amount.divide(unitPrice, 0, RoundingMode.DOWN).intValueExact();
        if (calculatedQuantity <= 0)
            throw new IllegalArgumentException("The amount must purchase at least one share");
        return calculatedQuantity;
    }

    private void validateMaximum(Long memberId, Long groupId, int additional, ShareProduct product) {
        if (product.getMaximumShares() != null) {
            int owned = calculateBalance(shareTransactionRepository.findLedgerByGroupId(groupId), memberId);
            if (owned + additional > product.getMaximumShares())
                throw new IllegalArgumentException("This purchase exceeds the member share limit");
        }
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0)
            throw new IllegalArgumentException("quantity must be greater than zero");
    }

    private ShareTransaction newTransaction(GroupMember member, ShareProduct product, ShareTransactionType type,
            int quantity, BigDecimal amount, String requestedReference) {
        return ShareTransaction.builder().groupMember(member).shareProduct(product).type(type).quantity(quantity)
                .unitPrice(product.getSharePrice()).totalAmount(amount).reference(reference(requestedReference))
                .transactionDate(LocalDateTime.now()).build();
    }

    private String reference(String requested) {
        return requested == null || requested.isBlank() ? "SHARE-" + UUID.randomUUID() : requested.trim();
    }

    private Map<Long, Integer> calculateBalances(List<ShareTransaction> ledger) {
        Map<Long, Integer> balances = new HashMap<>();
        for (ShareTransaction transaction : ledger) {
            int change = switch (transaction.getType()) {
                case PURCHASE, TRANSFER_IN, ADJUSTMENT -> transaction.getQuantity();
                case TRANSFER_OUT, REDEMPTION -> -transaction.getQuantity();
            };
            balances.merge(transaction.getGroupMember().getId(), change, Integer::sum);
        }
        return balances;
    }

    private int calculateBalance(List<ShareTransaction> ledger, Long memberId) {
        return calculateBalances(ledger).getOrDefault(memberId, 0);
    }

    private String memberName(GroupMember member) {
        return member.getMember().getFirstName() + " " + member.getMember().getLastName();
    }

    private ShareTransactionResponse mapTransaction(ShareTransaction transaction) {
        return ShareTransactionResponse.builder().id(transaction.getId())
                .groupMemberId(transaction.getGroupMember().getId())
                .memberName(memberName(transaction.getGroupMember()))
                .membershipNumber(transaction.getGroupMember().getMembershipNumber()).type(transaction.getType().name())
                .quantity(transaction.getQuantity()).unitPrice(transaction.getUnitPrice())
                .totalAmount(transaction.getTotalAmount())
                .reference(transaction.getReference()).transactionDate(transaction.getTransactionDate()).build();
    }
}
