package vikoba.service.contribution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.common.enums.PaymentAllocationType;
import vikoba.service.common.enums.PaymentMethod;
import vikoba.service.common.enums.PaymentStatus;
import vikoba.service.contribution.dto.PaymentResponse;
import vikoba.service.contribution.dto.RecordPaymentRequest;
import vikoba.service.contribution.entity.Payment;
import vikoba.service.contribution.entity.PaymentAllocation;
import vikoba.service.contribution.repository.PaymentAllocationRepository;
import vikoba.service.contribution.repository.PaymentRepository;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final VikobaGroupRepository groupRepository;

    @Transactional
    public PaymentResponse record(Long groupId, RecordPaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        VikobaGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        GroupMember member = request.getGroupMemberId() == null ? null
                : groupMemberRepository.findById(request.getGroupMemberId())
                        .orElseThrow(() -> new IllegalArgumentException("Group member not found"));
        if (member != null && !member.getGroup().getId().equals(groupId)) {
            throw new IllegalArgumentException("Member does not belong to this group");
        }

        Payment payment = Payment.builder()
                .group(group)
                .groupMember(member)
                .reference(reference(request.getReference(), "PAY"))
                .externalReference(request.getExternalReference())
                .amount(request.getAmount())
                .paymentMethod(parseMethod(request.getPaymentMethod()))
                .status(parseStatus(request.getStatus()))
                .paymentDate(LocalDateTime.now())
                .description(request.getDescription())
                .build();
        Payment saved = paymentRepository.save(payment);

        PaymentAllocation allocation = PaymentAllocation.builder()
                .payment(saved)
                .type(parseAllocation(request.getAllocationType()))
                .amount(request.getAmount())
                .referenceId(request.getAllocationReferenceId())
                .description(request.getDescription())
                .build();
        allocationRepository.save(allocation);
        return toResponse(saved, allocation);
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentResponse> list(Long groupId) {
        return paymentRepository.findByGroupIdWithMember(groupId).stream()
                .map(payment -> toResponse(
                        payment,
                        allocationRepository.findByPaymentId(payment.getId())
                                .stream()
                                .findFirst()
                                .orElse(null)
                ))
                .toList();
    }

    private PaymentMethod parseMethod(String value) {
        if (value == null || value.isBlank())
            return PaymentMethod.CASH;
        return PaymentMethod.valueOf(value.trim().toUpperCase().replace(' ', '_'));
    }

    private PaymentStatus parseStatus(String value) {
        if (value == null || value.isBlank())
            return PaymentStatus.COMPLETED;
        return PaymentStatus.valueOf(value.trim().toUpperCase());
    }

    private PaymentAllocationType parseAllocation(String value) {
        if (value == null || value.isBlank())
            return PaymentAllocationType.OTHER;
        return PaymentAllocationType.valueOf(value.trim().toUpperCase().replace(' ', '_'));
    }

    private String reference(String value, String prefix) {
        return value == null || value.isBlank() ? prefix + "-" + UUID.randomUUID() : value.trim();
    }

    private PaymentResponse toResponse(Payment payment, PaymentAllocation allocation) {
        GroupMember member = payment.getGroupMember();
        return PaymentResponse.builder()
                .id(payment.getId())
                .groupMemberId(member == null ? null : member.getId())
                .memberName(member == null ? "Group payment"
                        : member.getMember().getFirstName() + " " + member.getMember().getLastName())
                .membershipNumber(member == null ? null : member.getMembershipNumber())
                .reference(payment.getReference())
                .externalReference(payment.getExternalReference())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod().name())
                .status(payment.getStatus().name())
                .allocationType(allocation == null ? null : allocation.getType().name())
                .allocationReferenceId(allocation == null ? null : allocation.getReferenceId())
                .description(payment.getDescription())
                .paymentDate(payment.getPaymentDate())
                .build();
    }
}
