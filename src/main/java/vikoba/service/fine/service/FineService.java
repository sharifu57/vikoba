package vikoba.service.fine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.FineStatus;
import vikoba.service.fine.dto.*;
import vikoba.service.fine.entity.*;
import vikoba.service.fine.repository.*;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import vikoba.service.notification.SmsNotificationService;

@Service
@RequiredArgsConstructor
public class FineService {
    private final FineRepository fines;
    private final FineTypeRepository types;
    private final GroupMemberRepository members;
    private final VikobaGroupRepository groups;
    private final SmsNotificationService smsNotificationService;

    @Transactional(readOnly = true)
    public List<FineResponse> list(Long groupId) {
        requireGroup(groupId);
        return fines.findByGroupId(groupId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FineResponse create(Long groupId, FineInput input) {
        VikobaGroup group = requireGroup(groupId);
        GroupMember member = members.findById(input.getGroupMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Group member not found."));
        if (!member.getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Member does not belong to this group.");
        FineType type = resolveType(group, input);
        BigDecimal amount = positive(input.getAmount() == null ? type.getDefaultAmount() : input.getAmount());
        Fine fine = fines.save(Fine.builder().groupMember(member).fineType(type)
                .reference("FINE-"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT))
                .amount(amount).paidAmount(BigDecimal.ZERO)
                .issuedDate(input.getIssuedDate() == null ? LocalDate.now() : input.getIssuedDate())
                .reason(input.getReason()).status(FineStatus.UNPAID).build());
        smsNotificationService.send(member.getMember().getPhone(), "VIKOBA360: Taarifa ya faini. "
                + "Umewekewa faini ya TZS " + amount.toPlainString() + " (" + type.getName()
                + "). Tafadhali wasiliana na kikundi kwa maelezo.");
        return toResponse(fine);
    }

    @Transactional
    public FineResponse update(Long groupId, Long id, FineInput input) {
        Fine fine = fines.findById(id).orElseThrow(() -> new IllegalArgumentException("Fine not found."));
        if (!fine.getGroupMember().getGroup().getId().equals(groupId))
            throw new IllegalArgumentException("Fine does not belong to this group.");
        if (input.getPaymentAmount() != null) {
            BigDecimal payment = positive(input.getPaymentAmount());
            BigDecimal balance = fine.getAmount().subtract(fine.getPaidAmount());
            if (payment.compareTo(balance) > 0)
                throw new IllegalArgumentException("Payment exceeds the outstanding fine balance.");
            fine.setPaidAmount(fine.getPaidAmount().add(payment));
            fine.setStatus(
                    fine.getPaidAmount().compareTo(fine.getAmount()) >= 0 ? FineStatus.PAID : FineStatus.PARTIAL);
        }
        if ("WAIVED".equalsIgnoreCase(input.getStatus())) {
            fine.setStatus(FineStatus.WAIVED);
            fine.setPaidAmount(fine.getAmount());
        }
        if (input.getReason() != null)
            fine.setReason(input.getReason());
        Fine saved = fines.save(fine);
        if (input.getPaymentAmount() != null) {
            smsNotificationService.send(fine.getGroupMember().getMember().getPhone(),
                    "VIKOBA360: Malipo ya faini ya TZS " + input.getPaymentAmount().toPlainString()
                            + " yamepokelewa. Salio ni TZS "
                            + fine.getAmount().subtract(fine.getPaidAmount()).toPlainString() + ".");
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FineTypeResponse> types(Long groupId) {
        requireGroup(groupId);
        return types.findByGroupIdOrderByNameAsc(groupId).stream().filter(FineType::isActive).map(this::typeResponse)
                .toList();
    }

    @Transactional
    public FineTypeResponse createType(Long groupId, FineTypeRequest request) {
        VikobaGroup group = requireGroup(groupId);
        String code = normalizeCode(request.getCode(), request.getName(), "FINE");
        String name = requiredText(request.getName(), "Fine type name");
        BigDecimal amount = request.getDefaultAmount() == null ? BigDecimal.ZERO : request.getDefaultAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative.");
        }
        return types.findByGroupIdAndCode(groupId, code)
                .map(existing -> {
                    existing.setName(name);
                    existing.setDefaultAmount(amount);
                    existing.setDescription(request.getDescription());
                    existing.setActive(request.getActive() == null || request.getActive());
                    return typeResponse(types.save(existing));
                })
                .orElseGet(() -> typeResponse(types.save(FineType.builder()
                        .group(group)
                        .code(code)
                        .name(name)
                        .defaultAmount(amount)
                        .description(request.getDescription())
                        .active(request.getActive() == null || request.getActive())
                        .build())));
    }

    @Transactional
    public FineTypeResponse updateType(Long groupId, Long id, FineTypeRequest request) {
        FineType type = types.findById(id).orElseThrow(() -> new IllegalArgumentException("Fine type not found."));
        if (!type.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("Fine type does not belong to this group.");
        }
        String name = requiredText(request.getName(), "Fine type name");
        BigDecimal amount = request.getDefaultAmount() == null ? type.getDefaultAmount() : request.getDefaultAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative.");
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            String nextCode = normalizeCode(request.getCode(), name, type.getCode());
            if (!nextCode.equals(type.getCode())) {
                if (types.findByGroupIdAndCode(groupId, nextCode).filter(existing -> !existing.getId().equals(id))
                        .isPresent()) {
                    throw new IllegalArgumentException("A fine type with this code already exists in this group.");
                }
                type.setCode(nextCode);
            }
        }
        type.setName(name);
        type.setDefaultAmount(amount);
        type.setDescription(request.getDescription());
        type.setActive(request.getActive() == null || request.getActive());
        return typeResponse(types.save(type));
    }

    @Transactional
    public void deleteType(Long groupId, Long id) {
        FineType type = types.findById(id).orElseThrow(() -> new IllegalArgumentException("Fine type not found."));
        if (!type.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("Fine type does not belong to this group.");
        }
        type.setActive(false);
        types.save(type);
    }

    private FineType resolveType(VikobaGroup group, FineInput input) {
        if (input.getFineTypeId() != null)
            return types.findById(input.getFineTypeId()).filter(t -> t.getGroup().getId().equals(group.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("Fine type not found."));
        String name = input.getFineType() == null || input.getFineType().isBlank() ? "Other penalty"
                : input.getFineType().trim();
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return types.findByGroupIdAndCode(group.getId(), code)
                .orElseGet(() -> types.save(FineType.builder().group(group).code(code).name(name)
                        .defaultAmount(input.getAmount() == null ? BigDecimal.ZERO : input.getAmount()).active(true)
                        .build()));
    }

    private VikobaGroup requireGroup(Long id) {
        return groups.findById(id).orElseThrow(() -> new IllegalArgumentException("Group not found."));
    }

    private BigDecimal positive(BigDecimal v) {
        if (v == null || v.signum() <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero.");
        return v;
    }

    private String normalizeCode(String rawCode, String name, String fallback) {
        String candidate = rawCode == null || rawCode.isBlank() ? name : rawCode;
        String normalized = candidate.trim();
        if (normalized.isBlank()) {
            normalized = fallback == null ? "OTHER" : fallback;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return normalized.isBlank() ? "OTHER" : normalized;
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private FineResponse toResponse(Fine f) {
        var member = f.getGroupMember();
        return FineResponse.builder().id(f.getId()).groupMemberId(member.getId())
                .memberName(member.getMember().getFirstName() + " " + member.getMember().getLastName())
                .membershipNumber(member.getMembershipNumber()).fineTypeId(f.getFineType().getId())
                .fineTypeName(f.getFineType().getName()).reference(f.getReference()).amount(f.getAmount())
                .paidAmount(f.getPaidAmount()).balance(f.getAmount().subtract(f.getPaidAmount())).reason(f.getReason())
                .status(f.getStatus().name()).fineDate(f.getIssuedDate()).build();
    }

    private FineTypeResponse typeResponse(FineType t) {
        return FineTypeResponse.builder().id(t.getId()).code(t.getCode()).name(t.getName())
                .defaultAmount(t.getDefaultAmount()).description(t.getDescription()).active(t.isActive()).build();
    }
}
